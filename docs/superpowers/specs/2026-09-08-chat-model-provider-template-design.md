# Chat Model Provider Template Design

## Goal

Separate runtime model configuration from provider-specific transport behavior. Local vLLM, local Ollama,
remote OpenAI-compatible services, and DashScope must share one activation workflow while retaining their
own request options, authentication rules, HTTP transport, and streaming compatibility.

The change must also close two confirmed defects:

- A synchronous connection probe succeeds while the real streaming chat request reaches vLLM without a
  parseable request body and returns HTTP 400.
- Opening the model configuration page fails when no embedding runtime has been configured.

## Routing Matrix

| Deployment type | Compatibility preset | Provider |
| --- | --- | --- |
| `LOCAL` | `OPENAI_COMPATIBLE` | vLLM |
| `LOCAL` | `OLLAMA` | Ollama |
| `REMOTE` | `OPENAI_COMPATIBLE` | Remote OpenAI-compatible |
| `REMOTE` | `DASHSCOPE` | DashScope |

Unsupported combinations are rejected with a descriptive validation error. A local provider ignores and
clears API credentials. A remote provider requires an API key and relies on the existing credential cipher.

## Architecture

### Provider template

`AbstractChatModelProvider` implements the invariant workflow as final template methods:

1. validate the deployment/preset combination and common fields;
2. validate provider-specific fields;
3. create provider request options;
4. create the synchronous and streaming HTTP transports;
5. build the Spring AI `ChatModel`;
6. probe the same streaming path used by production chat;
7. normalize provider failures into a safe, actionable result without exposing credentials.

Subclasses override narrow hooks instead of duplicating the workflow:

- `VllmChatModelProvider` uses a vLLM-compatible streaming transport and sends
  `chat_template_kwargs.enable_thinking=false` for Qwen models.
- `OllamaChatModelProvider` retains the OpenAI-compatible endpoint contract and sends `think=false`.
- `RemoteOpenAiChatModelProvider` uses the standard remote OpenAI-compatible transport and requires an API key.
- `DashScopeChatModelProvider` sends `enable_thinking=false` and requires an API key.

`ChatModelProviderRouter` selects exactly one provider from deployment type and compatibility preset. Callers
do not inspect provider details.

### Runtime specification

Deployment type becomes an explicit part of the immutable runtime specification rather than being inferred
from whether an API key is blank. This prevents an unauthenticated remote gateway from being mistaken for a
local service and makes provider routing deterministic after a restart.

Existing database columns already contain `deployment_type` and `compatibility_preset`; no new chat model
configuration table is required. Reloading and activation reconstruct the same provider selection from those
persisted values.

### Compatibility facade

The existing `OpenAiCompatibleModelFactory` remains temporarily as a small facade for embedding construction
and compatibility with existing callers. Chat construction moves behind `ChatModelProviderRouter`. No business
chat service or agent executor may construct provider-specific models directly.

## Data Flow

### Test and activation

1. Administrator submits a candidate configuration.
2. The service creates a runtime specification including deployment type.
3. The router selects the provider.
4. The provider validates authentication and endpoint rules.
5. The provider builds a candidate runtime.
6. A bounded streaming probe consumes at least one response event or a valid terminal response.
7. Only a successful probe may be saved and atomically activated.
8. Failed probes leave the active database row and in-memory runtime unchanged.

The tool-calling checkbox triggers an additional capability probe after the base streaming probe. A basic
streaming failure must never be hidden by a successful synchronous tool probe.

### Production chat

Business chat continues to depend only on `DynamicChatModel`. The registry snapshot already contains the
provider-built `ChatModel`; therefore every synchronous and streaming invocation uses the exact transport and
options that passed activation testing.

### Restart

The database reloader reads `deployment_type`, selects the same provider through the router, builds the model,
and publishes it atomically. A missing or invalid configured provider is logged and does not prevent the
management application from starting.

## vLLM Transport Requirements

The vLLM implementation must be verified against the configured `/v1/chat/completions` endpoint in both
non-streaming and streaming modes. Its streaming request must carry a JSON body with `model`, `messages`,
`stream=true`, configured token/temperature values, and provider options. It must consume OpenAI-compatible
SSE chunks and terminate correctly on `[DONE]`.

Transport tests use a local HTTP fixture that records headers and body, preventing a regression where a POST
reaches the server with an empty body. A bounded live smoke test against the user's vLLM endpoint is performed
after the automated suite when that service is reachable.

## Embedding Configuration Page

Embedding configuration remains independent from chat providers. Its query endpoint becomes absence-safe:

- when an embedding runtime exists, return the active runtime and migration metadata;
- when none exists, return a successful response with `configured=false` and empty editable configuration;
- never turn an ordinary unconfigured state into the generic system-error response.

The frontend keeps chat and embedding load states separate. Failure of the embedding query cannot overwrite a
success message or make the chat configuration appear invalid.

## Error Handling

- Provider validation errors are HTTP 400 with a specific field or unsupported combination.
- Connectivity and protocol failures report the sanitized provider endpoint and whether the synchronous,
  streaming, or tool probe failed.
- API keys, authorization headers, request bodies containing credentials, and decrypted runtime specs are
  never logged or returned.
- vLLM response bodies may be surfaced only after length limiting and secret sanitization.
- An absent embedding runtime is normal state, not an exception.

## Testing

Tests are written before production changes and cover:

- every supported and unsupported router combination;
- local providers clearing credentials and remote providers requiring them;
- the template invoking provider hooks in the required order;
- vLLM streaming requests containing a non-empty JSON body and consuming SSE output;
- vLLM Qwen requests disabling thinking;
- Ollama and DashScope preserving their provider-specific options;
- test/save using the streaming probe and preserving the previous runtime on failure;
- database reload selecting the same provider as initial activation;
- embedding query returning `configured=false` without an active runtime;
- frontend loading chat and embedding states independently;
- existing model configuration, security, and business chat regression suites.

## Out of Scope

- Per-user model selection; the active chat model remains global for all users.
- Changing the embedding migration safety workflow.
- Supporting local protocols that are neither vLLM/OpenAI-compatible nor Ollama-compatible.
- Adding a second model gateway service or external configuration center.

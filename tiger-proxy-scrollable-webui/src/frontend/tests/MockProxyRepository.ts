///
///
/// Copyright 2021-2025 gematik GmbH
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///
/// *******
///
/// For additional notes and disclaimer from gematik and in case of changes by gematik find details in the "Readme" file.
///

// Mock data for development testing
export const mockMessageData = {
  "test-uuid-123": {
    content: `
      <div class="card msg-card mx-3 mt-3">
        <div class="card-header">
          <span>
            <button class="btn modal-button partner-message-button float-end" 
                    onclick="scrollToMessage('response-uuid-456',2)">
              <span class="icon is-small">
                <i class="fas fa-right-left"></i>
              </span>
            </button>
          </span>
          <h1 class="title ms-3 text-success">GET /api/users/123</h1>
        </div>
        <div class="card-content msg-content">
          <div class="msg-header-content">
            <div class="d-flex align-items-center mb-2">
              <h3 class="me-3">Request Headers</h3>
              <button class="btn btn-sm btn-outline-secondary header-toggle">
                <i class="fas fa-toggle-on"></i>
              </button>
            </div>
            <pre class="bg-light p-2 border rounded">Accept: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)
Content-Type: application/json
X-Request-ID: req-12345-abcde</pre>
          </div>
          <div class="msg-body-content">
            <div class="d-flex align-items-center mb-2">
              <h3 class="me-3">Request Body</h3>
              <button class="btn btn-sm btn-outline-secondary body-toggle">
                <i class="fas fa-toggle-on"></i>
              </button>
            </div>
            <pre class="bg-light p-2 border rounded json">{
  "filter": {
    "status": "active",
    "role": "admin"
  },
  "sort": "created_date",
  "limit": 50
}</pre>
          </div>
        </div>
      </div>
    `,
    uuid: "test-uuid-123",
    sequenceNumber: 1,
  },
  "response-uuid-456": {
    content: `
      <div class="card msg-card mx-3 mt-3">
        <div class="card-header">
          <span>
            <button class="btn modal-button partner-message-button float-end" 
                    onclick="scrollToMessage('test-uuid-123',1)">
              <span class="icon is-small">
                <i class="fas fa-right-left"></i>
              </span>
            </button>
          </span>
          <h1 class="title ms-3 text-primary">200 OK - Response</h1>
        </div>
        <div class="card-content msg-content">
          <div class="msg-header-content">
            <div class="d-flex align-items-center mb-2">
              <h3 class="me-3">Response Headers</h3>
              <button class="btn btn-sm btn-outline-secondary header-toggle">
                <i class="fas fa-toggle-on"></i>
              </button>
            </div>
            <pre class="bg-light p-2 border rounded">Content-Type: application/json
Cache-Control: no-cache
X-Response-Time: 45ms
Content-Length: 286
Set-Cookie: session=abc123; HttpOnly; Secure</pre>
          </div>
          <div class="msg-body-content">
            <div class="d-flex align-items-center mb-2">
              <h3 class="me-3">Response Body</h3>
              <button class="btn btn-sm btn-outline-secondary body-toggle">
                <i class="fas fa-toggle-on"></i>
              </button>
            </div>
            <pre class="bg-light p-2 border rounded json">{
  "id": 123,
  "name": "John Doe",
  "email": "john.doe@example.com",
  "role": "admin",
  "status": "active",
  "created_at": "2025-08-22T10:30:00Z",
  "last_login": "2025-08-22T14:15:30Z",
  "permissions": ["read", "write", "admin"]
}</pre>
          </div>
        </div>
      </div>
    `,
    uuid: "response-uuid-456",
    sequenceNumber: 2,
  },
  "error-uuid-789": {
    content: `
      <div class="card msg-card mx-3 mt-3">
        <div class="card-header">
          <h1 class="title ms-3 text-danger">404 Not Found</h1>
        </div>
        <div class="card-content msg-content">
          <div class="msg-header-content">
            <h3>Response Headers</h3>
            <pre class="bg-light p-2 border rounded">Content-Type: application/json
Content-Length: 58</pre>
          </div>
          <div class="msg-body-content">
            <h3>Response Body</h3>
            <pre class="bg-light p-2 border rounded json">{
  "error": "User not found",
  "code": 404,
  "timestamp": "2025-08-22T14:20:00Z"
}</pre>
          </div>
        </div>
      </div>
    `,
    uuid: "error-uuid-789",
    sequenceNumber: 3,
  },
};

// Mock ProxyRepository for development mode
export class MockProxyRepository {
  async fetchFullyRenderedMessage({ uuid }: { uuid: string }) {
    // Simulate network delay
    await new Promise((resolve) => setTimeout(resolve, 500));

    const mockData = mockMessageData[uuid as keyof typeof mockMessageData];

    if (!mockData) {
      throw new Error(`Message with UUID ${uuid} not found`);
    }

    return mockData;
  }

  async fetchResetMessages() {
    console.log("Mock: Reset messages called");
    return Promise.resolve();
  }

  async fetchQuitProxy() {
    console.log("Mock: Quit proxy called");
    return Promise.resolve();
  }
}

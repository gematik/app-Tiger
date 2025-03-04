# Tiger Proxy Web UI

First, build the detached app with:

```bash
npm run build:detached
```

This step is crucial for proper export functionality within the app.

Run the app in dev mode with:
```bash
npm run dev
```

Configure the proxy port as required in `vite.config.ts`:
```javascript
proxy: {
    '/api': {
        target: 'http://localhost:8080',
        ...
    }
}
```
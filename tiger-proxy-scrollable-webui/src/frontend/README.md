# Tiger Proxy Web UI

Start the project with:
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
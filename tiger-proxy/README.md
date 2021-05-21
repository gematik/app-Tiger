# Tiger-Proxy

A tool to give you flexible Routing from a to b and to protocol the traffic seen by the proxy.

## Standalone options


###Routes

Via `-r` or `--routes` you can specify routes for the proxy to map.
Example: 

`http://not.a.real.server;http://google.com,https://gog;https://google.com`

###Forward-Proxy

To specify a forward-proxy (a proxy-server that the Tiger-proxy should query) you can do so via `--proxy`:

`--proxy "192.168.110.10:3128"`

###Port

To customize the port that the tiger proxy should use simple give the value via `-p` or `--port`:

`-p 6667`

## Docker-Image

Run for example via 

`docker run --rm -p 6666:6666 --env ROUTES="http://not.a.real.server;http://google.com" tiger-proxy:latest`

Then you can query via 

`curl https://gog --proxy localhost:6666 -vv --insecure`

To see the current log go to `http://localhost:6666/rbel` (or, if you are using the proxy-server in your browser, simply visit http://rbel)

server {
    {{ if not .ssl }}
    listen {{ .port }} default_server;
    {{ else }}
    listen {{ .port }} default_server ssl;
    http2 on;
    {{ end }}

    include /etc/nginx/includes/server_params.conf;
    include /etc/nginx/includes/proxy_params.conf;

    {{ if .ssl }}
    include /etc/nginx/includes/ssl_params.conf;

    ssl_certificate /ssl/{{ .certfile }};
    ssl_certificate_key /ssl/{{ .keyfile }};
    {{ end }}

    {{ if not .leave_front_door_open }}
    location = /authentication {
        internal;
        proxy_pass              http://supervisor/auth;
        proxy_pass_request_body off;
        proxy_set_header        Content-Length "";
        proxy_set_header        X-Supervisor-Token "{{ env "SUPERVISOR_TOKEN" }}";

        # proxy_params.conf disables buffering for streamed queries, but nginx
        # only stores buffered responses in the cache. The auth reply is tiny.
        proxy_buffering         on;

        # Only successful checks are cached (failures are never cached).
        proxy_cache             auth_cache;
        proxy_cache_key         $http_authorization;
        proxy_cache_valid       200 1m;
        proxy_cache_bypass      $auth_cache_skip;
        proxy_no_cache          $auth_cache_skip;
    }
    {{ end }}

    location / {
        {{ if not .leave_front_door_open }}
        auth_request /authentication;
        auth_request_set $auth_status $upstream_status;
        {{ end }}

        proxy_pass http://backend;
    }
}

Initializing builder ...

Cloning source code from git ...

Using token authentication (x-access-token)

🔧 Pre-processing Dockerfile...

🔌 Initializing build environment...

⏳ Building image...

#1 [internal] load build definition from Dockerfile

#1 transferring dockerfile: 33.88kB done

#1 DONE 0.0s

#2 [internal] load metadata for docker.io/library/node:20-alpine

#2 DONE 0.7s

#3 [internal] load .dockerignore

#3 transferring context: 2B done

#3 DONE 0.0s

#4 [internal] load build context

#4 DONE 0.0s

#5 [base 1/1] FROM docker.io/library/node:20-alpine@sha256:f598378b5240225e6beab68fa9f356db1fb8efe55173e6d4d8153113bb8f333c

#5 resolve docker.io/library/node:20-alpine@sha256:f598378b5240225e6beab68fa9f356db1fb8efe55173e6d4d8153113bb8f333c 0.0s done

#5 DONE 0.0s

#4 [internal] load build context

#4 transferring context: 91.04MB 0.9s done

#4 DONE 1.0s

#5 [base 1/1] FROM docker.io/library/node:20-alpine@sha256:f598378b5240225e6beab68fa9f356db1fb8efe55173e6d4d8153113bb8f333c

#5 sha256:c7734204880fafbd6297ce1844e07c137d82ee814ac8b5fa6ac1a3659101d503 446B / 446B 0.0s done

#5 sha256:62e0e1181d88da8ed0e2fda221e9446ad0f09a004a375193ad7a38e31ec4eab4 1.26MB / 1.26MB 0.0s done

#5 sha256:589002ba0eaed121a1dbf42f6648f29e5be55d5c8a6ee0f8eaa0285cc21ac153 3.86MB / 3.86MB 0.1s done

#5 extracting sha256:589002ba0eaed121a1dbf42f6648f29e5be55d5c8a6ee0f8eaa0285cc21ac153 0.2s done

#5 sha256:d7f6aca05b78f475975eeac3898b7ef0a618937174c469650e05e81cbf8c5f46 43.23MB / 43.23MB 0.4s done

#5 extracting sha256:d7f6aca05b78f475975eeac3898b7ef0a618937174c469650e05e81cbf8c5f46

#5 extracting sha256:d7f6aca05b78f475975eeac3898b7ef0a618937174c469650e05e81cbf8c5f46 1.1s done

#5 DONE 1.7s

#5 [base 1/1] FROM docker.io/library/node:20-alpine@sha256:f598378b5240225e6beab68fa9f356db1fb8efe55173e6d4d8153113bb8f333c

#5 extracting sha256:62e0e1181d88da8ed0e2fda221e9446ad0f09a004a375193ad7a38e31ec4eab4 0.0s done

#5 extracting sha256:c7734204880fafbd6297ce1844e07c137d82ee814ac8b5fa6ac1a3659101d503 0.0s done

#5 DONE 1.7s

#6 [builder 1/4] WORKDIR /app

#6 ...

#7 [deps 1/4] RUN apk add --no-cache libc6-compat

#7 0.639 (1/3) Installing musl-obstack (1.2.3-r2)

#7 0.779 (2/3) Installing libucontext (1.3.3-r0)

#7 0.781 (3/3) Installing gcompat (1.1.0-r4)

#7 0.797 OK: 11.0 MiB in 21 packages

#7 ...

#6 [builder 1/4] WORKDIR /app

#6 DONE 6.7s

#7 [deps 1/4] RUN apk add --no-cache libc6-compat

#7 DONE 6.7s

#8 [deps 2/4] WORKDIR /app

#8 ...

#9 [runner 2/6] RUN addgroup --system --gid 1001 nodejs

#9 DONE 4.8s

#8 [deps 2/4] WORKDIR /app

#8 DONE 4.8s

#10 [deps 3/4] COPY package.json package-lock.json* ./

#10 ...

#11 [runner 3/6] RUN adduser --system --uid 1001 nextjs

#11 DONE 4.3s

#10 [deps 3/4] COPY package.json package-lock.json* ./

#10 DONE 6.1s

#12 [deps 4/4] RUN npm ci

#12 1.368 npm warn EBADENGINE Unsupported engine {

#12 1.368 npm warn EBADENGINE   package: 'camera-controls@3.1.2',

#12 1.368 npm warn EBADENGINE   required: { node: '>=22.0.0', npm: '>=10.5.1' },

#12 1.368 npm warn EBADENGINE   current: { node: 'v20.20.2', npm: '10.8.2' }

#12 1.368 npm warn EBADENGINE }

#12 11.53 npm warn deprecated fluent-ffmpeg@2.1.3: Package no longer supported. Contact Support at https://www.npmjs.com/support for more info.

#12 22.90 npm error code ECONNRESET

#12 22.90 npm error network aborted

#12 22.90 npm error network This is a problem related to network connectivity.

#12 22.90 npm error network In most cases you are behind a proxy or have bad network settings.

#12 22.90 npm error network

#12 22.90 npm error network If you are behind a proxy, please make sure that the

#12 22.90 npm error network 'proxy' config is set properly.  See: 'npm help config'

#12 22.90 npm notice

#12 22.90 npm notice New major version of npm available! 10.8.2 -> 11.12.1

#12 22.90 npm notice Changelog: https://github.com/npm/cli/releases/tag/v11.12.1

#12 22.90 npm notice To update run: npm install -g npm@11.12.1

#12 22.90 npm notice

#12 22.90 npm error A complete log of this run can be found in: /root/.npm/_logs/2026-04-06T00_49_10_515Z-debug-0.log

#12 ERROR: process "/bin/sh -c npm ci" did not complete successfully: exit code: 1

------

> [deps 4/4] RUN npm ci:
> 
> 22.90 npm error network In most cases you are behind a proxy or have bad network settings.

22.90 npm error network

22.90 npm error network If you are behind a proxy, please make sure that the

22.90 npm error network 'proxy' config is set properly.  See: 'npm help config'

22.90 npm notice

22.90 npm notice New major version of npm available! 10.8.2 -> 11.12.1

22.90 npm notice Changelog: https://github.com/npm/cli/releases/tag/v11.12.1

22.90 npm notice To update run: npm install -g npm@11.12.1

22.90 npm notice

22.90 npm error A complete log of this run can be found in: /root/.npm/_logs/2026-04-06T00_49_10_515Z-debug-0.log

------

🔴 Build Failed. Reason: build image: build failed: failed to solve: process "/bin/sh -c npm ci" did not complete successfully: exit code: 1
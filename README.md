# mc-proxy

This is a small java proxy for Minecraft servers with AWS EC2 integration.

Based on [RenegadeEagle/minecraft-redirect-proxy](https://github.com/RenegadeEagle/minecraft-redirect-proxy)

## Features

- Starts the instance when a player connects.
- Shutdowns the instance when the server has been empty for a specified amount of time.
- Basic whitelist support.

## Workflow

1. Proxy creates a fake online server.
2. Player initiates connection.
3. Proxy checks username against whitelist.
4. Starts the instance.
5. Once the server is up, hands-off the player to it.
6. Monitors the player count.
7. If empty, shutdowns the instance.

Monitoring is handled through the RCON console.

The proxy also handles instance startup failures, such as in the case of Spot instances.

Once the instance starts the proxy acts as a pass-through bypassing all the code used to fake the server including the built-in whitelist, you must use Minecraft's native whitelist.

## Issues

The RCON connection might break, which causes unexpected instance shutdowns.

## History

I needed a proxy server with EC2 integration so I borrowed some code from an existing project and modified it with the features I needed. This proxy was used for two months so that my friends and I could do a long-play without me spending a lot of money on EC2 (since Minecraft /w Fabric + optimization mods is quite heavy to run), I used a c6g.large spot instance which performed extremely well for a vanilla-like server.

Aside from the RCON issue which I never ended up fixing because it was quite rare and usually didn't require restarts, the proxy worked perfectly fine.

## Alternatives

Albeit [doctorray117/minecraft-ondemand](https://github.com/doctorray117/minecraft-ondemand) was available at the time and is a more robust implementation of the same workflow it is way more complex than what I needed and since I already had a (smaller) instance always up I ran the proxy on that which made everything pretty straightforward.

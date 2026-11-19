# Player Animator for Minecraft 1.12.2

A 1.12.2 / Forge port of [KosmX's Player Animator](https://github.com/KosmX/minecraftPlayerAnimator) (upstream branch `1.20`, commit `a17ecb8`, `1.0.2-rc1`).

The library evaluates player animations **on the client**: every client runs the same
animation layers for all visible players, so an animation that only depends on data both
sides already have needs no packet. An optional server-to-client packet exists for the
cases where the server is the only side that knows something happened (see
[Network layer](#network-layer)); it is idle unless a mod calls it.

## Requirements

| Item | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2859 |
| Java | 8 (bytecode target) |
| MixinBooter | 10.7+ (tested with 11.17) |

`MixinBooter` is required: the mod uses a small FML coremod to register its mixins.

## Build

Gradle 8.1.1 wrapper is included. Gradle itself needs a newer JDK, while the project
compiles with Java 8:

```bash
export JAVA_HOME="<path to JDK 21>"
./gradlew build
```

The output jar is `build/libs/playeranimator-1.12.2-<version>.jar`.

## Install

Drop the jar into `mods/` together with MixinBooter. The jar works on a dedicated server
too: there it only loads the config and the animation channel, the mixins are client-only
and are not registered on a server. Installing it on a server is optional and only needed
if you want the [network layer](#network-layer).

## Config

`config/playeranimator.cfg` is written on first launch (regular Forge config, so the
in-game config GUI can edit it too):

| Key | Category | Default | Meaning |
|---|---|---|---|
| `bodyAdjust` | `general` | `false` | Apply the clip's `body` position/rotation to the model root matrix. Off by default: a mod that already rotates the root itself (e.g. a look-aligned body) would otherwise get that rotation twice. |
| `debugModel` | `debug` | `false` | Log the biped part rotations once per second. |
| `debugFrames` | `debug` | `false` | Log the finished pose of every rendered frame. Very spammy, short sessions only. |
| `enable` | `network` | `true` | Accept animation packets from the server. Turn it off to ignore whatever a server sends. |

The JVM properties still win over the file, so existing command lines keep working:
`-Dplayeranimator.bodyAdjust=true`, `-Dplayeranimator.debugModel=true`,
`-Dplayeranimator.debugFrames=true`, `-Dplayeranimator.networkAnimations=false`.

## API

```java
// per-player animation stack (client side)
AnimationStack stack = PlayerAnimationAccess.getPlayerAnimLayer(clientPlayer);
stack.addAnimLayer(1000, new ModifierLayer<>());

// register a layer for every client player
PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
        new ResourceLocation("mymod", "my_layer"), 1000, player -> new ModifierLayer<>());

// play a clip
ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>)
        PlayerAnimationAccess.getPlayerAssociatedData(player).get(id);
layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(8, Ease.INOUTSINE),
        new KeyframeAnimationPlayer(clip));

// registry helpers
PlayerAnimationRegistry.getAnimation(id);
PlayerAnimationRegistry.serializeTextToString("\"hold_upper\"");
```

Clips use the upstream JSON format (Emotecraft or GeckoLib `.player_animation.json`).

## Network layer

Channel `playeranimator`, two clientbound packets, both only sent when a mod asks for it:

| Packet | Discriminator | Payload |
|---|---|---|
| `MessagePlayAnimation` | 0 | player uuid, clip id (`ResourceLocation`), fade-in ticks |
| `MessageStopAnimation` | 1 | player uuid, fade-out ticks |

The payload carries the clip *id*, not the clip: the receiving client resolves it through
`PlayerAnimationRegistry`, so the server never needs the animation files and clients can run
different packs. An id the client does not have is logged once and dropped.

Server side:

```java
ResourceLocation wave = new ResourceLocation("mymod", "wave");

// one player, their own client animates
PlayerAnimatorNetwork.playAnimation(targetPlayerMp, wave, 8);

// everybody watching the player, including the player
PlayerAnimatorNetwork.playAnimationForObservers(sourcePlayer, wave, 8);

// stop early, fades back to whatever the other layers produce
PlayerAnimatorNetwork.stopAnimation(targetPlayerMp, 8);
PlayerAnimatorNetwork.stopAnimationForObservers(sourcePlayer, 8);
```

Notes:

- The clip still has to be registered on every client
  (`PlayerAnimationRegistry.registerAnimation(id, clip)`) before the packet arrives, the
  same requirement as playing it locally.
- The animation lands on its own layer (`playeranimator:network`, priority `100`), so other
  layers keep running underneath and anything with a higher priority still wins.
- Observers are picked by dimension; 1.12.2 has no cheap "can this player see that entity"
  query, and a packet sent to a client that cannot see the player is just ignored.
- Players who miss the original call are caught up: the server resends active clips when
  they join, change dimension, or start tracking the source entity (e.g. walk into tracking
  range), so a late observer does not miss what is already playing.
- When a one-shot clip finishes, the client reports it back and the server drops the tracked
  state, so a later observer is not sent a clip that has already ended. Looping clips keep
  playing and are not cleared this way.
- Packets are dispatched to the client thread instead of being applied from netty.

## Pose mapping

The animation is applied to the vanilla `ModelBiped` parts after
`setRotationAngles`:

| part | 1.12.2 part |
|---|---|
| `head` | `bipedHead` (+ `bipedHeadwear`) |
| `torso` | `bipedBody` |
| `rightArm` / `leftArm` | `bipedRightArm` / `bipedLeftArm` |
| `rightLeg` / `leftLeg` | `bipedRightLeg` / `bipedLeftLeg` |
| `body` | model root matrix (`RenderPlayer#applyRotations`) |

## Known limitations

- **Bend is not supported.** 1.12.2 has no `ModelPart` / custom vertex consumer support;
  the upstream bendy-lib code is not ported.
- **No first-person animation.** Matches upstream `FirstPersonMode.NONE` semantics.
- **No automatic left-hand mirroring.** `minecraftApi.layers.LeftHandedHelperModifier`
  is provided for callers to add, as upstream does.
- Debug switches moved into `config/playeranimator.cfg` (see [Config](#config)).

## Notes

- This repository contains the library only. The TaCZ-Legacy compatibility layer
  (`com.tacz.legacy.client.compat.playeranimator`) lives in TaCZ-Legacy and is GPLv3.
- Original project: https://github.com/KosmX/minecraftPlayerAnimator (MIT).

## License

MIT. See [LICENSE](LICENSE).

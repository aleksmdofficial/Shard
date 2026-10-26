# Staff menus, punishment effects and statistics holograms

These modules are implemented in Shard without an additional plugin dependency.
`presentation.yml` is created on the first start. Reload it with `/shard reload`.

## Staff menu

Run `/shard gui` with `shard.gui` (operators by default). The menu includes online
and suspicious player lists with pagination, profile/history/monitor shortcuts,
statistics, recent violations, alert and nametag toggles, monitor settings and reload.
Labels follow `config.yml`'s `locale` (`en` or `ru`).

Actions run as the staff member, so granting `shard.gui` does not grant access to
reload, logs, monitoring or other commands. Viewing player lists requires
`shard.profile`; the suspicious list also requires `shard.suspicious.list`.
The menu is read-only: inventory clicks, shift transfers, hotbar swaps and drags
cannot move items while it is open. Disabling/reloading closes open menus.

## Punishment animations

Add an action to an existing command set in `punishments.yml`, immediately before
the command that actually enforces the punishment:

```yaml
commands:
  warn:
    - "[alert]"
    - "[log]"
    - "[animation] sparks"
    - "kick <player> Your combat looked unusual."
```

Styles: `sparks` (particles and sound) and `lightning` (visual lightning).
`[animation]` without an argument uses sparks. Neither effect changes the
player's health, inventory, flight, position or game mode. The sequence waits
`animations.duration-ticks` (1–100 ticks) before continuing. Existing punishment
sets are not automatically changed. Preview on yourself with
`/shard animation sparks` or `/shard animation lightning`, requiring
`shard.animation.preview` (operators by default). Preview never kicks or bans.

Timeout, disconnect, scheduling failure and entity retirement share an atomic
completion gate, so this action continues its sequence at most once. Disabling
animations completes pending delays. A concurrent animation for the same player
is skipped rather than replacing another sequence's continuation. Invalid styles
are warned about and skipped. This does not deduplicate separate punishment
sequences or persist pending punishments through a process restart.

## Public statistics holograms (Bukkit/Paper)

Set `holograms.enabled: true` in `presentation.yml`, then `/shard reload`.
Stand at the desired location and run:

```
/shard hologram create spawn
/shard hologram list
/shard hologram delete spawn
```

Requires `shard.hologram.manage` (operators by default). IDs use letters, digits,
underscore and hyphen, at most 32 characters. Up to 64 holograms and 12 lines per
hologram are supported. The first line is two blocks above the create location.
Everyone nearby can see them: put only public information in the templates.

Templates accept `&` colors and `{online}`, `{model}`, `{flags_24h}` placeholders.
Flags means logged violation records, not bans; the count is refreshed off-thread
every 30 seconds and shows `-` while storage is unavailable. Display refreshes
every five seconds. Names of suspected players are not displayed.

Locations are atomically saved in `holograms.yml` and restored after restart or
world/chunk load. Unloaded chunks are never force-loaded. Invisible marker armor
stands have no gravity, are invulnerable and are not saved into world chunks;
owned entities are removed on chunk unload, deletion, reload and plugin shutdown.
Folia currently supports the menu and punishment effects, but not these holograms.

## Server smoke test

1. Open the menu as an operator. Test pagination, profile, history and monitor.
2. Grant a non-operator only `shard.gui`. Verify restricted actions still fail.
3. Attempt shift-click, number-key swaps, double-click and inventory dragging.
4. Preview both effects. Test a temporary warn set on a test account, including
   disconnecting during the effect; the next action should execute once.
5. Create a hologram, unload/reload its chunk, restart the server and confirm
   that it appears exactly once. Disable and delete it; no stands should remain.
6. Confirm `/shard reload` applies each module's enable switch and templates.

The effects and GUI still require an in-game smoke test on the target Paper build.

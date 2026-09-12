# Vendored fork of oleksandrbalan/pagecurl

Origin: https://github.com/oleksandrbalan/pagecurl (tag `v1.5.1`)
License: Apache License 2.0 (same as Bunko; see the repository root `LICENSE`)
Copyright: Oleksandr Balan and pagecurl contributors

Vendored on 2026-07-03 because upstream has been dormant since 2024-02 and Bunko
needs behavior upstream does not expose. Modified files carry a fork notice header.

## Modifications

| File | Change |
|---|---|
| `page/CurlDraw.kt` | Added `drawCurlFront` / `drawCurlBack` so an arbitrary composable (the real incoming page) can be rendered on the flap's back face instead of a mirrored copy of the front. |
| `page/PageCurl.kt` | Added optional `backContent(current, forward)` parameter using the split front/back draw path. In leaf mode the backward turn is rendered as a forward turn in horizontally mirrored space (a spine-bound leaf cannot un-turn the way an edge-bound page does). `interactionsEnabled = false` attaches no native tap/drag handlers, turning PageCurl into a pure renderer for a host-owned gesture system. |
| `page/PageCurlState.kt` | Added `turnEndFractionX` (0.5f = fold stops at the spine, turning a full-page curl into a spread leaf turn). `next()`/`prev()` during an in-flight turn now commit and continue from the current fold instead of snapping flat (rapid-tap acceleration); both accept a `tapPosition` so the fold leads from the tapped corner. Leaf backward tap uses the mirrored mid keyframe. Interactive drive API for host gestures: `beginTurn` / `dragTurnTo` (per-event spring pursuit of the pointer, like the native drag) / `settleTurn`, reusing `NewEdgeCreator` plus the leaf mirror/clamp. |
| `page/DragGesture.kt`, `page/DragStartEnd.kt` | Drag release settles at the configured turn end edge instead of the far edge. Leaf mode clamps the crease so it cannot cross the spine, and feeds the backward drag through the mirrored space. |
| `page/DragCommonGesture.kt` | `DragConfig` gained `mirrorInputX` (leaf backward = mirrored forward) and `creaseRangeX` (spine clamp) applied when drag edges are created. |

Design background: `docs/0-17-pagecurl-restart.md` and `docs/0-17-pagecurl-library-analysis.md`.

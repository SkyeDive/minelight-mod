# outline.md

Outline of future plans

## Design
* Reevaluate whether inventory saving is needed
  * No need if other players' hotbars are natively visible to the client

## Core functionality
* Create GUI-data extractor
  1. ✅ Establish method to render the current GUI to a separate rendering target
  2. ✅ Preserve or simulate transparency of GUI rendering target
  3. ✅ Add backbuffer and per-frame change-detection
  4. ✅ Add event bouncers for GUI data
  5. Create mixin for receiving and rendering GUI data
  6. Create mixin for extracting and sending GUI data each tick
* Add handling for players disconnecting
* Sync hotbar HUD info between clients for HUD rendering
* Store gamemode and HUD data of possessors alongside inventory
* Rescale mouse positions in possession events to be screen percentage-based

## Polish
* Add commands to allow changing possessor/vessel status of other players
* Add more debug commands for maintenance
* Remove the generic testing command
* Render and animate held items for possessors

## Nice-to-have
* Move hardcoded command feedback to lang files
* Refactor internal project name (body-sharing -> minelight)

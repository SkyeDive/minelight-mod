# outline.md

Outline of future plans

## Design
* Reevaluate whether inventory saving is needed
  * No need if other players' hotbars are natively visible to the client

## Core functionality
* Create GUI-data extractor
* Add event bouncers for GUI data
* Add clientside handling for GUI data to render it as expected
* Store gamemode of possessors alongside inventory
* Add handling for players disconnecting
* Sync hotbar, health, hunger, armor, oxygen, experience data between clients for HUD rendering

## Polish
* Add commands to allow changing possessor/vessel status of other players
* Add more debug commands for maintenance
* Remove the generic testing command
* Render and animate held items for possessors

## Nice-to-have
* Move hardcoded command feedback to lang files
* Refactor internal project name (body-sharing -> minelight)

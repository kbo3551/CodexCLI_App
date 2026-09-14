# MEMORY

## [5] Office sizing, wandering, pop-out window
- Panel was rendering at 1x because HBox shrinks children to minWidth; office minWidth is now 500
  (>= 15*16*2) and the chat column has minWidth 420 so neither can crush the other
- Header actions: `↗` pop out to its own Stage (stylesheets/font/icons inherited, closing docks it
  back), `⤢` fill the centre area hiding the chat, `×` close
- `MainWindow.closeAuxiliaryWindows()` is called from app shutdown, otherwise a detached office
  keeps the JavaFX runtime alive and the process lingers
- Idle agents wander: `AgentWorker.walkTo(x, y, onArrival)` plus a wanderCooldown; a starting turn
  sets the cooldown to MAX_VALUE so work always beats wandering. `OfficeMap.wanderSpots()` excludes
  desks, seat lanes, walls and props
- package-windows.cmd retries the dist delete 5 times: after the app exits Windows holds its
  bundled runtime DLLs open briefly and jpackage fails otherwise
- Tests: 31 total (AgentWorkerTest covers the movement rules and the map invariants)

## [6] Popup surfaces need their own CSS
- ContextMenu/Menu/MenuItem and the ComboBox dropdown are separate popup windows: they had no app
  rules and fell back to Modena, and the global `.label` rule painted menu text white on white
- base.css now has a menus section (.context-menu, .menu-item + `> .label` states, submenu arrow,
  radio/check marks, scroll arrows) and popup-specific combo cells
  (`.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell`)
- Popups do not appear in a scene snapshot; UiPreviewTest now shows a real ContextMenu and
  ComboBox popup on an off-screen Stage (-3000,-3000, opacity 0) and snapshots them to
  menu-ko.png / combo-ko.png. Use this when touching popup styling
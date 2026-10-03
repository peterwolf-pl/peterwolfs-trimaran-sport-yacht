# Trimaran Sport Yacht — model brief

Fabric 26.3 / Java 25. Separate Wind & Sails add-on; no catamaran dependency.

## Reference and scale
- User screenshots: Gitana-style ocean racing trimaran, navy hulls and sails, yellow low canopy, arched crossbeams, wave-piercing bows, foils.
- Central hull: 256 model units / 16 blocks; outer hulls: 240 units / 15 blocks.
- Beam: 180 units / 11.25 blocks; outer hull centers: ±80 units.
- Masthead: 448 units / 28 blocks above hull origin.
- Existing Catamaran Sail Yacht: 128 × 80 units / 8 × 5 blocks.
- Original PW livery: pale blue contour lines, sail number 03, yellow cockpit. No copied sponsor logos.

## Implementation
1. Reuse local Fabric 26.3 registration, input, mooring, renderer and sailing integrations with a new namespace.
2. Build a hand-authored Java LayerDefinition from a testable geometry tree, not an entity JSON.
3. Share hull stations with deck support and collision volumes. Three independent rudder pivots; inclined jib-axis frame.
4. Generate a deterministic atlas with material tiles and continuous sail graphics; add inventory icon.
5. Verify geometry, UV bounds, deck footprint, transforms and compilation. Export the same geometry as OBJ for external inspection.

## Limits
Foils are fixed modeled appendages, not a new flight simulation. The yellow pod is a sealed racing shelter, not an enterable cabin or sleep interface. Auxiliary propulsion is retained for docking. No automatic Minecraft visual/GameTest run, deployment, git commit or publish.

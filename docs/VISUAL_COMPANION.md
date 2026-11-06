# Visual companion iteration

The overlay now presents notification text as a conservative maneuver cue: a schematic glyph, a separately sized observed distance, a short instruction and source time. It does not draw on or identify a geographic road.

Recognizes explicit English turn-left, turn-right, U-turn, continue-straight and roundabout wording. Compound instructions and unfamiliar languages retain their original text and a neutral location symbol. No inferred distances, countdowns, route completion or camera positions.

Motion is event-driven: one650ms path reveal for changed current text while the panel is visible, one700ms teal ring on the collapsed panda for a changed current Maps instruction. No repeating animation or signal on timestamp-only refresh. Android animator-disabled setting suppresses both. Saved observations stay muted and do not animate as fresh guidance.

Road highlighting is intentionally not implemented with screen-coordinate guesses. Accurate geographic alignment needs a separately validated map-camera/route source or a map controlled by Manzil. Next meaningful enhancements should show actual supplemental information (data age, missing location, verified accessibility or weather facts), not fabricated warnings.

Validation: parser tests cover observed distance splitting, misleading road names, compound instructions and unchanged unfamiliar-language text. Physical Samsung screenshot review covers the real Maps notification and overlay. Motion duration is implemented, not measured with frame-timing instrumentation.

Installed and visually checked on Samsung F56 over the existing live Maps session. Final build and lint passed;38 unit tests passed. Evidence: android-evidence/samsung-visual-reviewed.png. Motion triggers exclude distance-only changes. Freshness is a typed flag rather than inferred from display labels.

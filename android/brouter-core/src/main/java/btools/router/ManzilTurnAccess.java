package btools.router;

import java.util.ArrayList;
import java.util.List;

/** Tiny public adapter to upstream package-private hint fields; upstream code remains unmodified. */
public final class ManzilTurnAccess {
  public static final class Turn {
    public final int pointIndex, command, exitNumber;
    public final double distanceToNextMeters;
    Turn(VoiceHint h) { pointIndex=h.indexInTrack; command=h.cmd; exitNumber=h.getExitNumber(); distanceToNextMeters=h.distanceToNext; }
  }
  public static List<Turn> turns(OsmTrack track) {
    List<Turn> result = new ArrayList<>();
    if(track.voiceHints != null) for(VoiceHint h:track.voiceHints.list) result.add(new Turn(h));
    return result;
  }
}

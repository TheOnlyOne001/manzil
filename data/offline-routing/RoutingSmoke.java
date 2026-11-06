import btools.router.*;
import java.io.*;
import java.util.*;
public class RoutingSmoke {
 static OsmNodeNamed p(double lat,double lon,String name){OsmNodeNamed p=new OsmNodeNamed();p.ilat=(int)Math.round((lat+90)*1e6);p.ilon=(int)Math.round((lon+180)*1e6);p.name=name;return p;}
 public static void main(String[] a) throws Exception {
 File root=new File(a[0]);RoutingContext rc=new RoutingContext();rc.localFunction=new File(root,"profiles/walking.brf").getAbsolutePath();rc.memoryclass=128;rc.turnInstructionMode=4;rc.waypointCatchingRange=150;
 RoutingEngine e=new RoutingEngine(null,null,new File(root,"segments"),new ArrayList<>(Arrays.asList(p(Double.parseDouble(a[1]),Double.parseDouble(a[2]),"start"),p(Double.parseDouble(a[3]),Double.parseDouble(a[4]),"end"))),rc);e.quite=true;long start=System.currentTimeMillis();e.doRun(60000);
 if(e.getErrorMessage()!=null)throw new RuntimeException(e.getErrorMessage());OsmTrack t=e.getFoundTrack();if(t==null||t.nodes.size()<2)throw new RuntimeException("No geometry");System.out.println("distance_m="+t.distance+" points="+t.nodes.size()+" turns="+ManzilTurnAccess.turns(t).size()+" elapsed_ms="+(System.currentTimeMillis()-start));
 }
}


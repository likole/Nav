package cn.likole.nav;

import com.slamtec.slamware.robot.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by likole on 9/5/17.
 */

public class NavManager {

    public static List<Location> getLocations(String s) {
        List<Location> locations = new ArrayList<>();
        switch (s) {
            case "计算机学院":
                locations.add(new Location(20, 0, 0));
                locations.add(new Location(20, -50, 0));
                locations.add(new Location(40, -50, 0));
                break;
        }
        return locations;
    }
}

import java.io.File;

import core.LandingBeaconFederate;
import core.LandingBeaconFederateAmbassador;
import skf.config.ConfigurationFactory;
import model.LandingBeacon;
import model.Position;
import model.Quaternion;

public class LandingBeaconMain {
    
    private static final File conf = new File("config/conf.json");
    
    public static void main(String[] args) throws Exception {
        LandingBeacon beacon = new LandingBeacon(
                "LandingBeacon", 
                "LandingBeacon", 
                "AitkenBasinLocalFixed",
                new Position(0, -7100, -3000),  // Adjusted for best detection range
                new Quaternion(0, 0, 0, 1));
        
        LandingBeaconFederateAmbassador ambassador = new LandingBeaconFederateAmbassador();
        LandingBeaconFederate federate = new LandingBeaconFederate(ambassador, beacon);
        
        // start execution
        federate.configureAndStart(new ConfigurationFactory().importConfiguration(conf));
    }
}

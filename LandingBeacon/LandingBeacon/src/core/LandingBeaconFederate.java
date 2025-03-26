package core;

import hla.rti1516e.exceptions.AttributeNotDefined;
import hla.rti1516e.exceptions.AttributeNotOwned;
import hla.rti1516e.exceptions.CallNotAllowedFromWithinCallback;
import hla.rti1516e.exceptions.ConnectionFailed;
import hla.rti1516e.exceptions.CouldNotCreateLogicalTimeFactory;
import hla.rti1516e.exceptions.CouldNotOpenFDD;
import hla.rti1516e.exceptions.ErrorReadingFDD;
import hla.rti1516e.exceptions.FederateNotExecutionMember;
import hla.rti1516e.exceptions.FederateServiceInvocationsAreBeingReportedViaMOM;
import hla.rti1516e.exceptions.FederationExecutionDoesNotExist;
import hla.rti1516e.exceptions.IllegalName;
import hla.rti1516e.exceptions.InconsistentFDD;
import hla.rti1516e.exceptions.InteractionClassNotDefined;
import hla.rti1516e.exceptions.InteractionClassNotPublished;
import hla.rti1516e.exceptions.InteractionParameterNotDefined;
import hla.rti1516e.exceptions.InvalidInteractionClassHandle;
import hla.rti1516e.exceptions.InvalidLocalSettingsDesignator;
import hla.rti1516e.exceptions.InvalidObjectClassHandle;
import hla.rti1516e.exceptions.NameNotFound;
import hla.rti1516e.exceptions.NotConnected;
import hla.rti1516e.exceptions.ObjectClassNotDefined;
import hla.rti1516e.exceptions.ObjectClassNotPublished;
import hla.rti1516e.exceptions.ObjectInstanceNameInUse;
import hla.rti1516e.exceptions.ObjectInstanceNameNotReserved;
import hla.rti1516e.exceptions.ObjectInstanceNotKnown;
import hla.rti1516e.exceptions.RTIinternalError;
import hla.rti1516e.exceptions.RestoreInProgress;
import hla.rti1516e.exceptions.SaveInProgress;
import hla.rti1516e.exceptions.SynchronizationPointLabelNotAnnounced;
import hla.rti1516e.exceptions.UnsupportedCallbackModel;

import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Observable;
import java.util.Observer;
import java.util.TimeZone;

import model.LandingBeacon;
import model.Position;
import model.Spaceport;
import model.Lars;
import model.Lander;
import model.Quaternion;
import interactions.ConsoleColors;
import interactions.FederateMessage;
import interactions.KeyPressListener;
import siso.smackdown.frame.FrameType;
import siso.smackdown.frame.ReferenceFrame;
import skf.config.Configuration;
import skf.core.SEEAbstractFederate;
import skf.core.SEEAbstractFederateAmbassador;
import skf.exception.PublishException;
import skf.exception.SubscribeException;
import skf.exception.UnsubscribeException;
import skf.exception.UpdateException;
import skf.model.interaction.annotations.InteractionClass;
import skf.model.interaction.modeTransitionRequest.ModeTransitionRequest;
import skf.model.object.annotations.ObjectClass;
import skf.model.object.executionConfiguration.ExecutionConfiguration;
import skf.model.object.executionConfiguration.ExecutionMode;
import skf.synchronizationPoint.SynchronizationPoint;

public class LandingBeaconFederate extends SEEAbstractFederate implements Observer {

	private static final int MAX_WAIT_TIME = 10000;
	
	private LandingBeacon beacon = null;
	private Spaceport spaceport = null;
	private Lander lander = null;
	
	private String local_settings_designator = null;
	private ModeTransitionRequest mtr = null;
	
	private ReferenceFrame currentReferenceFrame = null;
	
private FederateMessage message = new FederateMessage();
    
    public LandingBeaconFederate(SEEAbstractFederateAmbassador seefedamb, LandingBeacon beacon) {
        super(seefedamb);
        this.beacon = beacon;
    }

    @SuppressWarnings("unchecked")
    public void configureAndStart(Configuration config) throws Exception {
        // 1. Configure SKF framework
        super.configure(config);

        // 2. Connect to RTI
        local_settings_designator = "crcHost=" + config.getCrcHost() + "\ncrcPort=" + config.getCrcPort();
        super.connectToRTI(local_settings_designator);

        // 3. Join the Federation Execution
        super.joinFederationExecution();

        // 4. Subscribe the Subject
        super.subscribeSubject(this);

        // 5. Check if the federate is a Late Joiner Federate
        if (!SynchronizationPoint.INITIALIZATION_STARTED.isAnnounced()) {

            // 6. Wait for the announcement of "initialization_completed"
            super.waitingForAnnouncement(SynchronizationPoint.INITIALIZATION_COMPLETED, MAX_WAIT_TIME);

            // 7. Subscribe Execution Control Object Class Attributes
            super.subscribeElement((Class<? extends ObjectClass>) ExecutionConfiguration.class);
            super.waitForElementDiscovery((Class<? extends ObjectClass>) ExecutionConfiguration.class, MAX_WAIT_TIME);

            // 8. Request Execution Configuration update
            while (super.getExecutionConfiguration() == null) {
                super.requestAttributeValueUpdate((Class<? extends ObjectClass>) ExecutionConfiguration.class);
                Thread.sleep(10);
            }

            // 9. Publish Mode Transition Request Interaction
            this.mtr = new ModeTransitionRequest();
            super.publishInteraction(this.mtr);

            // 10. Subscribe to the correct reference frame
            super.subscribeReferenceFrame(FrameType.AitkenBasinLocalFixed); // Updated from MoonCentricFixed

            // 11. Publish and Subscribe to necessary elements
            super.publishElement(beacon, "LandingBeacon"); // Publishes itself
            super.subscribeElement((Class<? extends ObjectClass>) Spaceport.class); // Subscribes to Spaceport
            super.subscribeElement((Class<? extends ObjectClass>) Lander.class); // Subscribes to Lander
            
            // NEW: 
            super.subscribeElement((Class<? extends ObjectClass>) Lars.class);

            // 12. Publish and Subscribe to interactions
            super.publishInteraction(this.message);
            super.subscribeInteraction((Class<? extends InteractionClass>) FederateMessage.class);

            // 13. Setup HLA Time Management
            super.setupHLATimeManagement();

            // Ready to execute
        } else {
            throw new RuntimeException("LandingBeacon is not a Late Joiner Federate");
        }

        // 14. Start simulation execution
        super.startExecution();
    }


    @Override
    protected void doAction() {
        System.out.println("[LandingBeacon] Monitoring Lander position...");
        
        try {
            super.updateElement(this.beacon);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    

    @Override
    public void update(Observable observable, Object arg) {
        System.out.println("[DEBUG] LandingBeacon received update: " + arg.getClass().getSimpleName());

        if (arg instanceof Lander) {
            Lander lander = (Lander) arg;

            if (lander.getPosition() != null) {
                System.out.println("[LandingBeacon] Received Lander Position: X=" 
                        + lander.getPosition().getX() + " Y=" 
                        + lander.getPosition().getY() + " Z=" 
                        + lander.getPosition().getZ());

                // Adjusted condition for detecting proximity
                if (lander.getPosition().getZ() <= -2000 && lander.getPosition().getZ() >= -3500) {
                    System.out.println("[DEBUG] Condition met: Sending LANDER_APPROACHING message...");
                    sendMessage("LandingBeacon", "Spaceport", "LANDER_APPROACHING", "Lander is nearing the beacon!");
                } else {
                    System.out.println("[DEBUG] Condition NOT met: Lander is still far away.");
                }
            } else {
                System.out.println("[LandingBeacon] Lander position is NULL.");
            }
        } 
        else if (arg instanceof Spaceport) {
            this.spaceport = (Spaceport) arg;

            if (this.spaceport.getPosition() != null) {
                System.out.println("[LandingBeacon] Received Spaceport Position: X="
                        + this.spaceport.getPosition().getX() + " Y="
                        + this.spaceport.getPosition().getY() + " Z="
                        + this.spaceport.getPosition().getZ());
            } else {
                System.out.println("[LandingBeacon] Spaceport Position is NULL.");
            }
        }
        else if (arg instanceof FederateMessage) {
            handleResponses((FederateMessage) arg);
        }
        else if (arg instanceof ReferenceFrame) {
        	this.currentReferenceFrame = (ReferenceFrame) arg;
        	System.out.println("[DEBUG] LandingBeacon received Reference Frame update: " + arg);
        }
        else if (arg instanceof ExecutionConfiguration) {
            super.setExecutionConfiguration((ExecutionConfiguration) arg);
        }
        else {
            System.out.println("[LandingBeacon] Unknown update received: " + arg.getClass().getSimpleName());
        }
    }
    
    private void sendMessage(String sender, String receiver, String type, String content) {
        this.message.setSender(sender);
        this.message.setReceiver(receiver);
        this.message.setMessageType(type);
        this.message.setContent(content);
        System.out.println("[DEBUG] Sending message: " + type + " to " + receiver);

        try {
            super.updateInteraction(this.message);
            System.out.println("[DEBUG] Message successfully sent.");
            ConsoleColors.logInfo("[LandingBeacon] Sent " + content + " to " + receiver);
        } catch (Exception e) {
        	System.out.println("[ERROR] Failed to send message: " + e.getMessage());
            ConsoleColors.logError("[LandingBeacon] Error sending message: " + e.getMessage());
        }
    }

    private void handleResponses(FederateMessage message) {
        switch (message.getMessageType()) {
            case "LANDING_CLEARANCE":
                ConsoleColors.logInfo("[LandingBeacon] Received " + message.getContent() + " from " + message.getReceiver());
                sendMessage("LandingBeacon", "Lander", "PROCEED_LANDING", "Landing is approved. Proceed!");
                break;
                
            case "LARS_STATUS":
                ConsoleColors.logInfo("[LandingBeacon] Received LARS status: " + message.getContent());
                sendMessage("LandingBeacon", "LARS", "ACKNOWLEDGED", "Beacon received status report. Proceed as planned.");
                break;

            default:
                ConsoleColors.logInfo("[LandingBeacon] Unknown message type: " + message.getMessageType());
                break;
        }
    }
}

package core;

import java.util.Observable;
import java.util.Observer;

import model.Lars;
import model.LandingBeacon;
import interactions.ConsoleColors;
import interactions.FederateMessage;
import siso.smackdown.frame.FrameType;
import siso.smackdown.frame.ReferenceFrame;
import skf.config.Configuration;
import skf.core.SEEAbstractFederate;
import skf.core.SEEAbstractFederateAmbassador;
import skf.model.interaction.modeTransitionRequest.ModeTransitionRequest;
import skf.model.object.annotations.ObjectClass;
import skf.model.object.executionConfiguration.ExecutionConfiguration;
import skf.model.object.executionConfiguration.ExecutionMode;
import skf.synchronizationPoint.SynchronizationPoint;
import skf.model.interaction.annotations.InteractionClass;

public class LarsFederate extends SEEAbstractFederate implements Observer {

    private static final int MAX_WAIT_TIME = 10000;

    private Lars lars;
    private LandingBeacon beacon;

    private String local_settings_designator;
    private ReferenceFrame currentReferenceFrame;
    private ModeTransitionRequest mtr = new ModeTransitionRequest();
    private FederateMessage message = new FederateMessage();
    
    // NEW
    private boolean messageSent = false;


    public LarsFederate(SEEAbstractFederateAmbassador fedAmb, Lars lars) {
        super(fedAmb);
        this.lars = lars;
    }

    @SuppressWarnings("unchecked")
    public void configureAndStart(Configuration config) throws Exception {
        super.configure(config);

        local_settings_designator = "crcHost=" + config.getCrcHost() + "\ncrcPort=" + config.getCrcPort();
        super.connectToRTI(local_settings_designator);
        super.joinFederationExecution();
        super.subscribeSubject(this);

        if (!SynchronizationPoint.INITIALIZATION_STARTED.isAnnounced()) {
            super.waitingForAnnouncement(SynchronizationPoint.INITIALIZATION_COMPLETED, MAX_WAIT_TIME);

            super.subscribeElement((Class<? extends ObjectClass>) ExecutionConfiguration.class);
            super.waitForElementDiscovery((Class<? extends ObjectClass>) ExecutionConfiguration.class, MAX_WAIT_TIME);

            while (super.getExecutionConfiguration() == null) {
                super.requestAttributeValueUpdate((Class<? extends ObjectClass>) ExecutionConfiguration.class);
                Thread.sleep(10);
            }

            super.publishInteraction(mtr);
            super.subscribeReferenceFrame(FrameType.AitkenBasinLocalFixed);

            super.publishElement(lars, "LARS");
            super.subscribeElement((Class<? extends ObjectClass>) LandingBeacon.class);

            super.publishInteraction(message);
            super.subscribeInteraction((Class<? extends InteractionClass>) FederateMessage.class);

            super.setupHLATimeManagement();
        } else {
            throw new RuntimeException("LARS is not a Late Joiner Federate");
        }

        super.startExecution();
    }

    @Override
    protected void doAction() {
        // sendMessage("LARS", "LandingBeacon", "REPORT_ENV_CONDITIONS", "Dust levels nominal. Terrain stable.");
    	if (!messageSent) {
            sendMessage("LARS", "LandingBeacon", "LARS_STATUS", "Surface scan completed. No hazards detected.");
            messageSent = true;
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof ExecutionConfiguration) {
            super.setExecutionConfiguration((ExecutionConfiguration) arg);
        } else if (arg instanceof FederateMessage) {
            handleResponses((FederateMessage) arg);
            FederateMessage msg = (FederateMessage) arg;
            System.out.println("[LARS] Received message: " + msg.getMessageType() + " - " + msg.getContent());
        } else if (arg instanceof ReferenceFrame) {
            this.currentReferenceFrame = (ReferenceFrame) arg;
            System.out.println("[DEBUG] LARS received Reference Frame update.");
        } else if (arg instanceof LandingBeacon) {
            this.beacon = (LandingBeacon) arg;
            System.out.println("[DEBUG] LARS received LandingBeacon update.");
        } else {
            System.out.println("[LARS] Unknown update: " + arg.getClass().getSimpleName());
        }
    }

    private void sendMessage(String sender, String receiver, String type, String content) {
        this.message.setSender(sender);
        this.message.setReceiver(receiver);
        this.message.setMessageType(type);
        this.message.setContent(content);
        System.out.println("[DEBUG] LARS sending message: " + type + " to " + receiver);

        try {
            super.updateInteraction(this.message);
            ConsoleColors.logInfo("[LARS] Sent message: " + content + " to " + receiver);
        } catch (Exception e) {
            ConsoleColors.logError("[LARS] Failed to send message: " + e.getMessage());
        }
    }

    private void handleResponses(FederateMessage message) {
        switch (message.getMessageType()) {
            case "ACK_REPORT_RECEIVED":
                ConsoleColors.logInfo("[LARS] Received ACK from " + message.getSender() + ": " + message.getContent());
                break;
            default:
                ConsoleColors.logInfo("[LARS] Unknown message type: " + message.getMessageType());
                break;
        }
    }
}

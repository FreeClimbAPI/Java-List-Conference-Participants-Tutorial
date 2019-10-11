/* 
 * 1. AFTER RUNNING PROJECT WITH COMMAND: 
 *    `gradle build && java -Dserver.port=0080 -jar build/libs/gs-spring-boot-0.1.0.jar`
 * 2. CALL NUMBER ASSOCIATED WITH THE ACCOUNT (CONFIGURED IN PERSEPHONY DASHBOARD)
 * 3. EXPECT TO BE JOINED TO A CONFERENCE WITH AGENT_PHONE
 * 4. RUN CURL COMMAND TO GET LIST OF QUEUES:
 *    `curl {baseUrl}/conferenceParticipants`
 * 5. EXPECT JSON TO BE RETURNED:
 *    [{"uri":"/Accounts/{accountId}/Conferences/{conferenceId}/Participants/{callId}",
 *      "dateCreated":"{dateCreated}",
 *      "dateUpdated":"{dateUpdated}",
 *      "revision":1,"callId":"{callId}",
 *      "conferenceId":"{conferenceId}",
 *      "accountId":"{accountId}",
 *      "talk":true,"listen":true,
 *      "startConfOnEnter":true}, MORE ELEMENTS OF THE SAME FORMAT]
*/

package main.java.list_conference_participants;

import org.springframework.web.bind.annotation.RestController;

import com.vailsys.persephony.percl.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.vailsys.persephony.api.PersyException;
import com.vailsys.persephony.webhooks.conference.ConferenceCreateActionCallback;

import com.vailsys.persephony.webhooks.percl.OutDialActionCallback;

import com.vailsys.persephony.webhooks.StatusCallback;
import com.vailsys.persephony.api.call.CallStatus;

import com.vailsys.persephony.webhooks.conference.LeaveConferenceUrlCallback;

import com.vailsys.persephony.api.PersyClient;
import com.vailsys.persephony.api.conference.ConferenceUpdateOptions;
import com.vailsys.persephony.api.conference.ConferenceStatus;

import com.vailsys.persephony.api.conference.participant.Participant;
import com.vailsys.persephony.api.conference.participant.ParticipantList;
import com.vailsys.persephony.api.conference.participant.ParticipantsSearchFilters;
import java.util.ArrayList;

@RestController
public class ListConferenceParticipantsController {
  // Get base URL, accountID, and authToken from environment variables
  private String baseUrl = System.getenv("HOST");
  private String accountId = System.getenv("ACCOUNT_ID");
  private String authToken = System.getenv("AUTH_TOKEN");

  public String conferenceId;

  // To properly communicate with Persephony's API, set your Persephony app's
  // VoiceURL endpoint to '{yourApplicationURL}/InboundCall' for this example
  // Your Persephony app can be configured in the Persephony Dashboard
  @RequestMapping(value = {
      "/InboundCall" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<?> inboundCall() {
    // Create an empty PerCL script container
    PerCLScript script = new PerCLScript();

    // Create a conference once an inbound call has been received
    script.add(new CreateConference(baseUrl + "/ConferenceCreated"));

    // Convert PerCL container to JSON and append to response
    return new ResponseEntity<>(script.toJson(), HttpStatus.OK);
  }

  @RequestMapping(value = {
      "/ConferenceCreated" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<?> conferenceCreated(@RequestBody String str) {
    PerCLScript script = new PerCLScript();

    ConferenceCreateActionCallback conferenceCreateActionCallback;
    try {
      conferenceCreateActionCallback = ConferenceCreateActionCallback.createFromJson(str);
      conferenceId = conferenceCreateActionCallback.getConferenceId();

      script.add(new Say("Please wait while we attempt to connect you to an agent."));

      // Make OutDial request once conference has been created
      String agentPhoneNumber = "+12175527482";// lookupAgentPhoneNumber(); // implementation of
                                               // lookupAgentPhoneNumber() is left up to the developer
      OutDial outDial = new OutDial(agentPhoneNumber, conferenceCreateActionCallback.getFrom(),
          baseUrl + "/OutboundCallMade" + "/" + conferenceId, baseUrl + "/OutboundCallConnected" + "/" + conferenceId);
      outDial.setIfMachine(OutDialIfMachine.HANGUP);
      script.add(outDial);

    } catch (PersyException pe) {
      System.out.println(pe.getMessage());
    }

    return new ResponseEntity<>(script.toJson(), HttpStatus.OK);
  }

  @RequestMapping(value = {
      "/OutboundCallMade/{conferenceId}" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<?> outboundCallMade(@PathVariable String conferenceId, @RequestBody String str) {
    PerCLScript script = new PerCLScript();

    OutDialActionCallback outDialActionCallback;
    try {
      // Convert JSON into a call status callback object
      outDialActionCallback = OutDialActionCallback.createFromJson(str);
      // Add initial caller to conference
      AddToConference addToConference = new AddToConference(conferenceId, outDialActionCallback.getCallId());

      // set the leaveConferenceUrl for the inbound caller, so that we can terminate
      // the conference when they hang up
      addToConference.setLeaveConferenceUrl(baseUrl + "/LeftConference");
      script.add(addToConference);

    } catch (PersyException pe) {
      System.out.println(pe.getMessage());
    }

    return new ResponseEntity<>(script.toJson(), HttpStatus.OK);
  }

  @RequestMapping(value = {
      "/OutboundCallConnected/{conferenceId}" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<?> outboundCallConnected(@PathVariable String conferenceId, @RequestBody String str) {
    PerCLScript script = new PerCLScript();

    StatusCallback statusCallback;
    try {
      // Convert JSON into a call status callback object
      statusCallback = StatusCallback.fromJson(str);

      // Terminate conference if agent does not answer the call. Can't use PerCL
      // command since PerCL is ignored if the call was not answered.
      if (statusCallback.getCallStatus() != CallStatus.IN_PROGRESS) {
        terminateConference(conferenceId);
        return new ResponseEntity<>(script.toJson(), HttpStatus.OK);
      }

      script.add(new AddToConference(conferenceId, statusCallback.getCallId()));
    } catch (PersyException pe) {
      System.out.println(pe.getMessage());
    }

    return new ResponseEntity<>(script.toJson(), HttpStatus.OK);
  }

  @RequestMapping(value = {
      "/LeftConference" }, method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
  public ResponseEntity<?> leftConference(@RequestBody String str) {
    LeaveConferenceUrlCallback leaveConferenceUrlCallback;
    try {
      // Convert JSON into a leave conference callback object
      leaveConferenceUrlCallback = LeaveConferenceUrlCallback.createFromJson(str);
      // Terminate the conference when the initial caller hangs up. Can't use PerCL
      // command since PerCL is ignored if the caller hangs up.
      terminateConference(leaveConferenceUrlCallback.getConferenceId());

    } catch (PersyException pe) {
      System.out.println(pe.getMessage());
    }

    return new ResponseEntity<>("", HttpStatus.OK);
  }

  private static void terminateConference(String conferenceId) throws PersyException {
    PersyClient client = new PersyClient(System.getenv("ACCOUNT_ID"), System.getenv("AUTH_TOKEN"));

    // Create the ConferenceUpdateOptions and set the status to terminated
    ConferenceUpdateOptions conferenceUpdateOptions = new ConferenceUpdateOptions();
    conferenceUpdateOptions.setStatus(ConferenceStatus.TERMINATED);
    client.conferences.update(conferenceId, conferenceUpdateOptions);
  }

  @RequestMapping("/conferenceParticipants")
  public ArrayList<Participant> listConferenceParticipants() {
    ParticipantsSearchFilters filters = new ParticipantsSearchFilters();
    filters.setTalk(true);
    filters.setListen(true);
    try {
      PersyClient client = new PersyClient(accountId, authToken); // Create PersyClient object
      // Invoke get method to retrieve initial list of conference participant info
      ParticipantList participantList = client.conferences.getParticipantsRequester(conferenceId).get();

      // Check if the list is empty by checking the total size
      if (participantList.getTotalSize() > 0) {
        // retrieve all conference participant information from the server
        while (participantList.getLocalSize() < participantList.getTotalSize()) {
          participantList.loadNextPage();
        }

        // Create a list of the conference participants
        ArrayList<Participant> list = participantList.export();

        // Loop through the list to process conference participant information
        for (Participant participant : list) {
          // Do some processing
          System.out.println(participant);
        }
        return list;
      }
    } catch (PersyException ex) {
      ex.printStackTrace();
    }
    return null;
  }
}
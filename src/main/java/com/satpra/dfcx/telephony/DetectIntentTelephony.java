package com.satpra.dfcx.telephony;


import java.io.FileInputStream;
import java.io.IOException;


import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.BidiStream;
import com.google.cloud.dialogflow.cx.v3beta1.AudioEncoding;
import com.google.cloud.dialogflow.cx.v3beta1.AudioInput;
import com.google.cloud.dialogflow.cx.v3beta1.InputAudioConfig;
import com.google.cloud.dialogflow.cx.v3beta1.QueryInput;
import com.google.cloud.dialogflow.cx.v3beta1.QueryResult;
import com.google.cloud.dialogflow.cx.v3beta1.SessionName;
import com.google.cloud.dialogflow.cx.v3beta1.SessionsClient;
import com.google.cloud.dialogflow.cx.v3beta1.SessionsSettings;
import com.google.cloud.dialogflow.cx.v3beta1.StreamingDetectIntentRequest;
import com.google.cloud.dialogflow.cx.v3beta1.StreamingDetectIntentResponse;
import com.google.protobuf.ByteString;

/**
* @author Shivaprasad Mohanrao
*/
//@Component
public class DetectIntentTelephony {

	public static void main(String[] args) throws Exception{

		//testing purpose only
	}



	//DialogFlow API Detect Intent sample with audio files processes as an audio stream.
	public static String[] detectIntentStream (String projectId, String locationId, String agentId, String sessionId, String audioFilePath, String language) 
			throws ApiException, IOException {
		//System.out.println("detectIntentStream Started, audioFilePath="+audioFilePath);

		String[] detectionArr = new String[2];
		SessionsSettings.Builder sessionsSettingsBuilder = SessionsSettings.newBuilder();
		if (locationId.equals("global")) {
			sessionsSettingsBuilder.setEndpoint("dialogflow.googleapis.com:443");
		} else {
			sessionsSettingsBuilder.setEndpoint(locationId + "-dialogflow.googleapis.com:443");
		}
		SessionsSettings sessionsSettings = sessionsSettingsBuilder.build();

		// Instantiates a client
		try (SessionsClient sessionsClient = SessionsClient.create(sessionsSettings)) {
			// Set the session name using the projectID (my-project-id), locationID (global), agentID
			// (UUID), and sessionId (UUID).
			// Using the same `sessionId` between requests allows continuation of the conversation.
			SessionName session = SessionName.of(projectId, locationId, agentId, sessionId);

			// Instructs the speech recognizer how to process the audio content.
			// Note: hard coding audioEncoding and sampleRateHertz for simplicity.
			// Audio encoding of the audio content sent in the query request.
			InputAudioConfig inputAudioConfig =
					InputAudioConfig.newBuilder().setAudioEncoding(AudioEncoding.AUDIO_ENCODING_LINEAR_16)
					.setSampleRateHertz(16000) // sampleRateHertz = 16000
					.build();
			System.out.println("detectIntentStream after audio processing");
			// Build the AudioInput with the InputAudioConfig.
			AudioInput audioInput = AudioInput.newBuilder().setConfig(inputAudioConfig).build();

			// Build the query with the InputAudioConfig.
			QueryInput queryInput = QueryInput.newBuilder().setAudio(audioInput).setLanguageCode(/* "en-US" */language).build();

			// Create the Bidirectional stream
			BidiStream<StreamingDetectIntentRequest, StreamingDetectIntentResponse> bidiStream =
					sessionsClient.streamingDetectIntentCallable().call();

			// The first request must **only** contain the audio configuration:
			bidiStream.send(
					StreamingDetectIntentRequest.newBuilder()
					.setSession(session.toString())
					.setQueryInput(queryInput)
					.build());

			try (FileInputStream audioStream = new FileInputStream(audioFilePath)) {
				System.out.println("Entering inner try block");
				// Subsequent requests must **only** contain the audio data.
				// Following messages: audio chunks. We just read the file in fixed-size chunks. In reality
				// you would split the user input by time.
				byte[] buffer = new byte[4096];
				int bytes;
				while ((bytes = audioStream.read(buffer)) != -1) {
					AudioInput subAudioInput =AudioInput.newBuilder().setAudio(ByteString.copyFrom(buffer, 0, bytes)).build();
					QueryInput subQueryInput = QueryInput.newBuilder().setAudio(subAudioInput)
							.setLanguageCode(/* "en-US" */language).build();
					bidiStream.send((StreamingDetectIntentRequest.newBuilder().setQueryInput(subQueryInput).build()));
				}
			} catch (Exception e) {
				System.out.println("INNER EXCEPTION:::: "+ e.getMessage());
			}

			// Tell the service you are done sending data.
			bidiStream.closeSend();

			//System.out.println("bidiStream =" + bidiStream);
			String playPrompt = null;
			String intent = null;
			
			
			for (StreamingDetectIntentResponse response : bidiStream) {
				System.out.println("Temp " + response.getRecognitionResult());
				
				QueryResult queryResult = response.getDetectIntentResponse().getQueryResult();
				
				
				if(queryResult.getResponseMessagesList().size() > 0  && 
						queryResult.getResponseMessagesList().get(0) != null && 
						//queryResult.getIntent().getDisplayName().toString() != " " && 
						queryResult.getIntent().getDisplayName() != null && 
						queryResult.getResponseMessagesList().get(0).getText() != null /*&& 
						queryResult.getResponseMessagesList().get(0).getText().toString() != " "*/) {

					System.out.println("Play Prompt : " + queryResult.getResponseMessagesList().get(0).getText());
					playPrompt = queryResult.getResponseMessagesList().get(0).getText().getText(0);
					System.out.println("playPromptString=" + playPrompt);
					intent = queryResult.getIntent().getDisplayName();
					break;
				}
			}
			
			
			detectionArr[0] = playPrompt;
			System.out.println("Detected Intent:"+ intent);
			detectionArr[1] = intent;
		} catch (Exception e) {
			System.out.println("OUTER EXCEPTION:::: "+ e.getMessage());
		}
		return detectionArr;
	}
}

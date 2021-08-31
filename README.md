# dialogflow-cx-telephony-integration
Integration of Google Dialogflow CX with Twilio Platform to Create a Voice Bot a.k.a Conversational IVR

## Instructions to setup and run CoVoice Bot
1. Clone the dialogflow-telephony-integration project in IDE.(https://github.com/shivaprasadmohanrao/dfcx_telephony_integration)
2. DialogFlow CX Bot Agent app flow : https://github.com/shivaprasadmohanrao/dfcx_telephony_integration/blob/main/GoogleDialogFlowCX-AgentExport/exported_agent_TeleponyIntegration_dfcx.blob
3. Use case Call flow diagram : https://github.com/shivaprasadmohanrao/dfcx_telephony_integration/blob/main/src/main/resources/CoVociceBotDocs/DialogFlowCX_TelephonyIntegration_IVRCallFlowLatest.pdf
4. Telephony Integration Architecture Diagram : https://github.com/shivaprasadmohanrao/dfcx_telephony_integration/blob/main/src/main/resources/CoVociceBotDocs/DFCX_Telephony_ArchitectureDiagram.jpg
5. Steps used in Java (algorithm) https://github.com/shivaprasadmohanrao/dfcx_telephony_integration/blob/main/src/main/resources/CoVociceBotDocs/JavaTelephonySteps.jpg
6. You will need to set environment variable named GOOGLE\_APPLICATION\_CREDENTIALS in run configuration of IDE. This will allow the java program running onyour local to access the bot deployed in Google Dialogflow CX. Please see steps below:
<br>a. If you are running in Eclipse, right click on TelephonyStart.java > Run As > Run Configurations... > Environment tab > New... > Set Name = GOOGLE\_APPLICATION\_CREDENTIALS > Set Value = Absolute path of dialogflow-cx-305201-7899257d6273.json file > Save > Apply > Close.
<br>b. For IDEA, please check <a href="https://www.jetbrains.com/help/objc/add-environment-variables-and-program-arguments.html#add-environment-variables">this</a>.
4. Right click on TelephonyStart.java > Run As > Java Application. Application will start on port number 4567.
5. To reach the CoVoice bot, we need to map the application URL to a phone number in Twilio platform. For this webhook URL needs to be configured which should be a public URL.
<br>We will publish http://localhost:4567 as an external URL using ngrok. Download ngrok <a href="https://ngrok.com/download">here</a>.
In TelephonyStart.java this variable must be changed "static String ngrok_url" to ngrok url, which is configured in Twiio webhook.
6. In cmd or terminal run<br>
./ngrok http 4567
<br>This will give you URL some thing like this http://57*****01-4900-502b-523d-c845-c90d-b7a8-e87c.ngrok.io or https://57******1-4900-502b-523d-c845-c90d-b7a8-e87c.ngrok.io.
This will allow your localhost web app running on port 4567 to be exposed to outside internet world. Our Java web app continues to listen on port 4567, ngrok exposes the above url which will be mapped to localhost:4567 url. This makes all the call control to be done on java web app(TelephonyStart.java to be very specific)
7. Login to <a href="https://console.twilio.com/">Twilio console</a>. Set the above URL for Voice webhook in Twilio for redirection so that when anyone calls the Twilio number, this URL will be hit.
8. To connect with CoVoice bot, dial +1-443-960-7077 from your phone device. ( under free version of Twilio i have got only US numbers, so this works only on US numbers)
9. Dial +1-443-960-7077 --> (Twilio Prompt for free version, skip by pressing any key) --> CoVoice Bot Welcome prompt --> Select language --> Ask question naturally from your preffered language --> Enter mobile number --> Enter OTP --> Enter beneficiary Id --> Receive vaccine certiicate on whatsapp as pdf.
10. Sending Whatsapp message also need Twilio platform, so before testing this feature, have to enable Twilio Whatsapp sandbox and need to join by typing secret code from your whatsapp to twilio whatsapp number(+1-415-523-2018) ex: join found-bradley. This will enable your whatsapp to receive messages from Twilio platform.

Please refer this WIKI pdf for complete details of integration, architecture, use cases, snapshots etc : https://github.com/shivaprasadmohanrao/dfcx_telephony_integration/blob/main/CoVoiceBot-Wiki.pdf

I am posting/uploading demo videos that i have recorded during my testing of this bot app.
DFCX_TelephonyIntegration - Google Drive for 4k/HD videos
(Please contact me at shivaprasad.mohanrao@gmail.com for access)
Also please use the github link for low resolution videos:(sorry for the quality of videos)
https://github.com/shivaprasadmohanrao/dfcx_telephony_integration/tree/main/src/main/resources/CoVoiceBot-DemoVideos
The logs from my local for the above use cases and demo can be found here:
https://github.com/shivaprasadmohanrao/dfcx_telephony_integration/tree/main/src/main/logs

For any other siggestions or issues please contact me on shivaprasad.mohanrao@gmail.com

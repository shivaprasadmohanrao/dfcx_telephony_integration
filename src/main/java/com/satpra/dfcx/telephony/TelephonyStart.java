package com.satpra.dfcx.telephony;

import static spark.Spark.post;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.io.FileUtils;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.google.common.hash.Hashing;
import com.satpra.dfcx.telephony.model.ConfirmOTPRequest;
import com.twilio.Twilio;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Gather;
import com.twilio.twiml.voice.Record;
import com.twilio.twiml.voice.Say;

import spark.Spark;;

/**
 * Start class for Twilio Telephony integration to Google DialogFlow CX 
 * @author Shivaprasad Mohanrao
 * Idea here is to show that we can do an integration of Google DialogFlow CX API exposed Through DetectIntentStream for audio content/voice content from human
 * with Telephony/IVR platform, here am using Twilio platform.
 * 
 * I am calling this use case or flow as CoVoice Bot, used specifically keeping Senior citizens in India, who has difficulty using Mobile app or web portals. 
 * However the UI/UX design, Senior Citizen always have problems/issues using it, have seen this with my parents, especially when they were struglling to get vaccine certificate. 
 * Though its very easy to get certificate frm COWIN portal but was not the case with my parents. That is when i wanted to build a IVR application, where user can just call 
 * and talk freely as they are speaking with humans. 
 * Here this voice bot is supporting English and Hindi as language. We can extend this to many India languages like Tamil, Telugu by just adding test utterances in DialogFlow CX bot.
 * 
 * Twilio gets the telephone call from PSTN/VOIP and triggers a webhook to this application.
 * This app builds voice based application, to collect speech input, dtmf input.
 * === API's Used:
	* 1. COWIN Portal - India Govt
	* 2. Google DialogFlow CX - DetectIntentStream
	* 3. WhatsApp API - Sending WhatsApp messages
	* 4. Twilio SDK - Voice Control ===
 * === CoVoice Capabilities :
	* 1. Receive call
	* 2. Play Prompt
	* 3. Gather user utterance - Voice input, DTMF input
	* 4. Multi Channel - Telephony and Whatsapp(SMS)
	* 5. Multi Lingual - English and Hindi Supported
	* 6. Rich Media data exchange, PDF, JPEG, DeepLink etc ===
 * === Other Specialities of this bot :
 	* 1. Multi Lingual - adding new languages is very simple on DialogFlow CX
	* 2. Multi Channel Support - WhatsApp, Telephone, SMS, Email, Deeplink, push notification, two way SMS etc
	* 3. Multi media support - image, pdf, url's, location, contact etc
	* 4. Mixed language support is also supported - accidently i found this working without any issue wrt Google DialogFlow CX ===

 * This app also shows how we can use multi channel to do self service like sending messages to WhatsApp, send images, PDF's, DeepLink URL and other media resources very easily.
 *  === Google API integration is called from line number 167 DetectIntentStream ===
 *  === After Intent detection logic: call deflection ===
		Here based on user intent other than "Certificate DownLoad" Intent, I am only detecting the input, support with step by step solution by detecting the 
		call to other cahnnel like web, whatsapp etc.
		1. First collect the intent
		2. set message & media file(pdf)
		3. Send WhatsApp message including Emoji's, rich content, URL, with CoVoiceBot logo -> jpeg file
		4. Send Intent specific message on Whatsapp 
		5. Send Intent specific PDF, step by step instruction or url
		6. Thank you note on voice/telephony call and thank you logo on WhatsApp
		
		This shows the rich multi langual, multi media , multi channel support using Google DialogFlow X, Telephony platform, MEssaging channel.
    === After Intent detection logic: call deflection ===
 * 
 */
public class TelephonyStart {
	
	//ngrok url: To expose the localhost over the internet/http, for twilio/telephony platform to webhook this paplication
	//STEP1: You need to set GOOGLE CLOUD CREDENTIALS json file set in as ENVIRONMENT VAR
	//STEP2: Get TWILIO Account, and join Whatsapp sandbox
	//STEP3: CHANGE THIS TO YOUR TWILIO ACCOUNT DETAILS on line number 226 & 227
		//String ACCOUNT_SID = "A****40e7fc899fc50296d5f922731***e"; //Twilio Account - Account SID
		//String AUTH_TOKEN = "5*****4996eda59b99337d67a854****c";//Twilio Account - Auth Token
	//STEP4: Set Webhook URL in Twilio Account to redirect the call to this aaplication
	//STEP5 : Change this URL to your local ngrok url as per your host machine
	//for any issues in setting up please refer the README (https://github.com/shivaprasadmohanrao/dfcx_telephony_integration) or WIKI 
	static String ngrok_url = "http://****-****-4900-502b-523d-c845-c90d-b7a8-e87c.ngrok.io/";
	static VoiceResponse twiml = null;//for generating twiml(twilio xml) for voice dialog control
	static RestTemplate restTemplate = new RestTemplate();
	static String languageSelected = "";//user selected Language
	public static String userLastIntent = ""; 
	public static String mediaFile = "";
	public static String userPhoneNumber = "";
	//customized rest template builder for downloading pdf from COWIN API's
	static RestTemplateBuilder restTbc = new RestTemplateBuilder(new RestTemplateCustomizer() {
		@Override
		public void customize(RestTemplate restTemplate) {
			restTemplate = TelephonyStart.restTemplate;
		}
	});
	//Transaction ID generated from COWIN API after OTP is sent, used for future COWIN API validations
	static String otpTrxnId = "";
	//This is the Bearer authentication token generated after COWIN validates user OTP entered on phone call
	static String otpToken = "";
	//User entered 14 digit Beneficiary ID
	/**Note: If we have approved COWIN API, we might not need Beneficiary ID to be collected from user. 
	*Idea here is to show the capability of collecting dtmf inputs*/
	static String benId = "";
	
	//SparkJava Main method - Start of the program
	public static void main(String[] args) {
		//serve static files like pdf, jpeg, media files from this project over the http (internet)
		Spark.staticFiles.location("/static");

		// 1. Get the preferred language
		post("record-call", (req, res) -> {
			System.out.println("1. Voice Bot Interaction Started.");
			//prepare first Dialog prompt, expected to collect User preferred language
			Say welcomeLanguageSelection = new Say.Builder("Welcome to co voice bot. Please press one for English. Hindi ke liye doh dabayen").
					voice(Say.Voice.POLLY_ADITI).build();
			//Preparing dialog call control on telephony platform using twiml, here we are gathering 1 digit input with 10s of noinput timeout
			Gather gatherLanguage = new Gather.Builder().timeout(10).numDigits(1).action("get-intent").inputs(Gather.Input.DTMF).say(welcomeLanguageSelection).build();
			//finally we are using above say and gather twiml objects to construct a dialog
			twiml= new VoiceResponse.Builder().gather(gatherLanguage).build();

			return twiml.toXml();

		});

		// 2. Record user input for NLU intent  
		post("get-intent", (req, res) -> {
			// Check language selection here
			String welcomeMessage = "";
			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("2. User LanguageSelection in DTMF ======= " + map.get("Digits"));
			String language = map.get("Digits");
				if("2".equals(language)) {// Hindi
					welcomeMessage = "Dhanyawaad, aaj mein aapki kya madad kar sakti hoon?";
					languageSelected = "hi-IN";}
				else {// English
					welcomeMessage = "Thank you, how can I help you today?";
					languageSelected = "en-US";}
			Say welcome = new Say.Builder(welcomeMessage).voice(Say.Voice.POLLY_ADITI).build();
			Record record = new Record.Builder().maxLength(20).playBeep(false).action("/post-recording-action").build();
			twiml = new VoiceResponse.Builder().say(welcome).record(record).build();
			return twiml.toXml();

		});

		// 3. Convert recorded file, check intent and get phone #
		post("post-recording-action", (req, res) -> {
			
			System.out.println("3. Audio Recording for caller Intent.");
			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("Recording URL ======= " + map.get("RecordingUrl"));
			// Get recording URL here
			saveAudioFile(map.get("RecordingUrl"));
			String audioPath = convertFile();
			Say sayPhoneNumber = null;
			// Shiva Project - The actual integration from my application to Google DialogFlow CX application using DetectIntentStream()
			String[] strArr = DetectIntentTelephony.detectIntentStream("dialogflow-cx-305201", "global", "bf6beb5a-cbe4-408b-853d-d8dca0e89468", "txt123", 
					audioPath, languageSelected);
			System.out.println("User's Intent Recognized from Google dialogFlow CX as ===== " + strArr[1]);
			// Check user intent
			if(strArr[1].equalsIgnoreCase("downloadcertificate")) {
				Say sayDownload = new Say.Builder(strArr[0]).voice(Say.Voice.POLLY_ADITI).build();
				
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					sayPhoneNumber = new Say.Builder(" कृपया को-विन के साथ registered अपना दस अंकों का मोबाइल नंबर enter करे ").voice(Say.Voice.POLLY_ADITI).build();} 
				else {
					sayPhoneNumber = new Say.Builder("Please enter or say your 10 digit phone number registered with kowin").voice(Say.Voice.POLLY_ADITI).build();}

				Gather gatherPhoneNumber = new Gather.Builder().timeout(5).numDigits(10).action("/generate-otp")
						.inputs(Arrays.asList(Gather.Input.SPEECH, Gather.Input.DTMF)).say(sayPhoneNumber).build();

				twiml= new VoiceResponse.Builder().say(sayDownload).gather(gatherPhoneNumber).build();

			} else if(strArr[1].equalsIgnoreCase("helpline") || strArr[1].equalsIgnoreCase("cowinregistration") || strArr[1].equalsIgnoreCase("cowindonts")
					|| strArr[1].equalsIgnoreCase("certificateverification") || strArr[1].equalsIgnoreCase("vaccineeffects")
					|| strArr[1].equalsIgnoreCase("cowinavailability") || strArr[1].equalsIgnoreCase("cowinissue")) {
				Say sayHelpline = new Say.Builder(strArr[0]).voice(Say.Voice.POLLY_ADITI).build();
					if(strArr[1].equalsIgnoreCase("helpline")){
						userLastIntent = "helpline";
					}else if(strArr[1].equalsIgnoreCase("cowinregistration")) {
						userLastIntent = "cowinregistration";
					}else if(strArr[1].equalsIgnoreCase("cowindonts")) {
						userLastIntent = "cowindonts";
					}else if(strArr[1].equalsIgnoreCase("certificateverification")) {
						userLastIntent = "certificateverification";
					}else if(strArr[1].equalsIgnoreCase("vaccineeffects")) {
						userLastIntent = "vaccineeffects";
					}else if(strArr[1].equalsIgnoreCase("cowinavailability")) {
						userLastIntent = "cowinavailability";
					}else if(strArr[1].equalsIgnoreCase("cowinissue")) {
						userLastIntent = "cowinissue";
					}else {
						userLastIntent = "else";
					}
				
					if("hi-IN".equalsIgnoreCase(languageSelected)) {
						sayPhoneNumber = new Say.Builder(" कृपया को-विन के साथ registered अपना दस अंकों का मोबाइल नंबर enter करे ").voice(Say.Voice.POLLY_ADITI).build();} 
					else {
						sayPhoneNumber = new Say.Builder("Please enter or say your 10 digit phone number registered with kowin").voice(Say.Voice.POLLY_ADITI).build();}
	
					Gather gatherPhoneNumber = new Gather.Builder().timeout(5).numDigits(10).action("/thank-you")
							.inputs(Arrays.asList(Gather.Input.SPEECH, Gather.Input.DTMF)).say(sayPhoneNumber).build();
					if("hi-IN".equalsIgnoreCase(languageSelected)) {
						sayPhoneNumber = new Say.Builder(" कृपया को-विन के साथ registered अपना दस अंकों का मोबाइल नंबर enter करे ").voice(Say.Voice.POLLY_ADITI).build();} 
					else {
						sayPhoneNumber = new Say.Builder("Please enter or say your 10 digit phone number registered with kowin").voice(Say.Voice.POLLY_ADITI).build();}
					twiml= new VoiceResponse.Builder().say(sayHelpline).gather(gatherPhoneNumber).build();
			} else { // nomatch
				// we can handle no match, no input scenarios based on telephony platform and requirement
			}
			// End intent check
			return twiml.toXml();
		});
		//Added this for common return or intents other than download to send whatsapp message and play thanks and end the call
	/**
		Here based on user intent other than "Certificate DownLoad" Intent, I am only detecting the input, support with step by step solution by defectign the call to other cahnnel like web, whatsapp etc.
		1. First i collect the intent
		2. set message & media file(pdf)
		3. Send WhatsApp message including Emoji's, rich content, URL, with CoVoiceBot logo -> jpeg file
		4. Send Intent specific message on Whatsapp 
		5. Send Intent specific PDF, step by step instruction or url
		6. Thank you note on voice/telephony call and thank you logo on WhatsApp
		
		This shows the rich multi langual, multi media , multi channel support using Google DialogFlow X, Telephony platform, MEssaging channel.
	*/
		post("thank-you", (req, res) -> {
			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("Inside Thank You ======= " + map.get("Digits"));
			userPhoneNumber = map.get("Digits");
			//STEP 4: CHANGE THIS TO YOUR TWILIO ACCOUNT DETAILS
			String ACCOUNT_SID = "A****40e7fc899fc50296d5f922731***e"; //Twilio Account - Account SID
			String AUTH_TOKEN = "5*****4996eda59b99337d67a854****c";//Twilio Account - Auth Token
			Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
			String message = "";
			if("helpline".equalsIgnoreCase(userLastIntent)) {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "कृपया हेल्पलाइन नंबरों की सूची देखें:     \n" +
							"📞 हेल्पलाइन नंबर: *+91-11-23978046* (☎️ टोल फ्री - *1075* )\n" + 
							"📞 तकनीकी हेल्पलाइन नंबर: *0120-4473222* \n" + 
							"📞 बच्चा: *1098* \n" + 
							"📞 मानसिक स्वास्थ्य: *08046110007* \n" + 
							"📞 वरिष्ठ नागरिक: *14567* \n" + 
							"सुझाव और अधिक जानकारी के लिए कृपया उस 🏥 टीकाकरण केंद्र से भी संपर्क करें जहां आपने कोविड की खुराक ली थी। ";
					mediaFile = ngrok_url + "contactus.pdf";
				} 
				else {
					message = "Please find list of helpline numbers:     \n" + 
							"📞 Helpline Number: *+91-11-23978046* (☎️Toll free - *1075* ) \n" + 
							"📞 Technical Helpline Number: *0120-4473222* \n" + 
							"📞 Child: *1098* \n" + 
							"📞 Mental Health: *08046110007* \n" + 
							"📞 Senior Citizens: *14567* \n" + 
							"Also please contact the 🏥 Vaccination Centre where you took covid dose, for suggestions & more information. ";
					mediaFile = ngrok_url + "contactus.pdf";
				}
			} else if("cowinregistration".equalsIgnoreCase(userLastIntent)) {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "कृपया पंजीकरण विवरण प्राप्त करें:"
							+ "*CoWIN* द्वार(🖥️ *WhatsApp: https://selfregistration.cowin.gov.in/* ) or 📱 *UMANG app* or  📱 *Arogya Setu* app, नीचे दिए गए किसी भी आईडी प्रूफ का उपयोग करना: 🆔 \n" + 
							"ए) आधार कार्ड 🪧 \n" + 
							"बी) ड्राइविंग लाइसेंस 💳 \n" + 
							"सी) पैन कार्ड  💳 \n" + 
							"डी) पासपोर्ट 🛂 \n" + 
							"ई) पेंशन पासबुक 🧾 \n" + 
							"एफ) एनपीआर स्मार्ट कार्ड  💳 \n" + 
							"जी) वोटर आईडी (ईपीआईसी EPIC) 🛗 \n" + 
							"एच) विशिष्ट विकलांगता आईडी ( *यूडीआईडी UDID* ) 📇 \n" + 
							"एई) राशन कार्ड";
					mediaFile = ngrok_url + "registration.pdf";
				} 
				else {
					message = "Please find the registration details: "
							+ "*CoWIN* portal(🖥️ *WhatsApp: https://selfregistration.cowin.gov.in/* ) or 📱 *UMANG app* or  📱 *Arogya Setu* app, using any of the below ID proofs: 🆔 \n" + 
							"a) Aadhaar card 🪧 \n" + 
							"b) Driving License 💳 \n" + 
							"c) PAN card  💳 \n" + 
							"d) Passport 🛂 \n" + 
							"e) Pension Passbook 🧾 \n" + 
							"f) NPR Smart Card  💳 \n" + 
							"g) Voter ID ( *EPIC* ) 🛗 \n" + 
							"h) Unique Disability ID ( *UDID* ) 📇 \n" + 
							"i) Ration Card";
					mediaFile = ngrok_url + "registration.pdf";
				}
			} else if("cowindonts".equalsIgnoreCase(userLastIntent)) {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "📃 क्या करें और क्या न करें (DOs & Donts) के बारे में अधिक जानकारी के लिए, कृपया इस पीडीएफ को देखें";
					mediaFile = ngrok_url + "cowindonts.pdf";
				} 
				else {
					message = "📃 For more information on DOs and DONTs, please refer to this PDF";
					mediaFile = ngrok_url + "cowindonts.pdf";
				}
			}else if("certificateverification".equalsIgnoreCase(userLastIntent)) {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "📃 अपने दस्तावेज़ को सत्यापित करने के बारे में अधिक जानकारी के लिए, कृपया यह पीडीएफ देखें";
					mediaFile = ngrok_url + "verifycertificate.pdf";
				} 
				else {
					message = "📃 For more information on verifying your document, please refer to this PDF";
					mediaFile = ngrok_url + "verifycertificate.pdf";
				}
			}
			else if("vaccineeffects".equalsIgnoreCase(userLastIntent)) {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "📃 टीके के दुष्प्रभावों के बारे में अधिक जानकारी के लिए, कृपया इस पीडीएफ को देखें";
					mediaFile = ngrok_url + "vaccineeffects.pdf";
				} 
				else {
					message = "📃 For more information on vaccine side effects, please refer to this PDF";
					mediaFile = ngrok_url + "vaccineeffects.pdf";
				}
			}
			else if("cowinavailability".equalsIgnoreCase(userLastIntent)) {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "👉 स्लॉट की उपलब्धता के बारे में अधिक जानकारी के लिए, कृपया यह पीडीएफ देखें";
					mediaFile = ngrok_url + "registration.pdf";} 
				else {
					message = "👉 For more information on availability of slots, please refer to this PDF";
					mediaFile = ngrok_url + "registration.pdf";}
			}else if("cowinissue".equalsIgnoreCase(userLastIntent)) {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "👉 COWIN पोर्टल या शिकायत पर किसी भी मुद्दे पर अधिक जानकारी के लिए, कृपया इस पीडीएफ को देखें";
					mediaFile = ngrok_url + "cowinissue.pdf";} 
				else {
					message = "👉 For more information on any issue on COWIN portal or Grievance, please refer to this PDF";
					mediaFile = ngrok_url + "cowinissue.pdf";}
			}else {
				if("hi-IN".equalsIgnoreCase(languageSelected)) {
					message = "👉 उस पर अधिक जानकारी के लिए, कृपया इस पीडीएफ को देखें";
					mediaFile = ngrok_url + "FAQ.pdf";} 
				else {
					message = "👉 For more information on that, please refer to this PDF";
					mediaFile = ngrok_url + "FAQ.pdf";
				}
				
			}
			
			com.twilio.rest.api.v2010.account.Message.creator( 
					new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
					new com.twilio.type.PhoneNumber("whatsapp:+14155238886"), 
					"Message From CoVoice Bot").setMediaUrl(Arrays.asList(URI.create(ngrok_url  + "CoVoiceBotLogo.jpeg"))).create();
			com.twilio.rest.api.v2010.account.Message.creator( 
					new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
					new com.twilio.type.PhoneNumber("whatsapp:+14155238886"),message).create();
			com.twilio.rest.api.v2010.account.Message.creator( 
					new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
					new com.twilio.type.PhoneNumber("whatsapp:+14155238886"),"pdf").
					setMediaUrl(Arrays.asList(URI.create(mediaFile))).create();
			com.twilio.rest.api.v2010.account.Message.creator( 
					new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
					new com.twilio.type.PhoneNumber("whatsapp:+14155238886"),"THANK YOU!").
					setMediaUrl(Arrays.asList(URI.create(ngrok_url  + "thankyou.jpeg"))).create();
			Say say1 = new Say.Builder("Thank you for calling Co Voice bot. Good bye!").voice(Say.Voice.POLLY_ADITI).build();

			twiml = new VoiceResponse.Builder().say(say1).build();
			return twiml.toXml();

			});
		//end
		// 4. Generate OTP and get it from the user
		post("generate-otp", (req, res) -> {

			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("4. In generate-otp, User Mobile Number Collected ======= " + map.get("Digits"));
			String phoneNumber = map.get("Digits");
			userPhoneNumber = map.get("Digits");

			otpTrxnId = generateOTPTelephony(phoneNumber);
			otpTrxnId = otpTrxnId.replace("{\"txnId\":\"", "");
			otpTrxnId = otpTrxnId.replace("\"}", "");
			System.out.println("trxnId = " + otpTrxnId);
			Thread.sleep(2000);
			Say getOtp;
			if("hi-IN".equalsIgnoreCase(languageSelected)) {
				getOtp = new Say.Builder("Dhanyawaad. प्रमाणीकरण के लिए, आपके koWin registered मोबाइल नंबर पर भेजा गया 6 digit O T P अपने मोबाइल पर दबाएं").voice(Say.Voice.POLLY_ADITI).build();} 
			else {
				getOtp = new Say.Builder("Thanks. For authentication, Please enter the 6 digit kowin O T P received on your mobile, just now").voice(Say.Voice.POLLY_ADITI).build();}
			Gather gatherOtp = new Gather.Builder().timeout(20).numDigits(6).action("/validate-otp")
					.inputs(Arrays.asList(Gather.Input.SPEECH, Gather.Input.DTMF)).say(getOtp).build();
			
			twiml= new VoiceResponse.Builder().gather(gatherOtp).build();
			return twiml.toXml();

		});

		// 5. Validate OTP and get beneficiary id from the user
		post("validate-otp", (req, res) -> {

			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("5.In validate-otp, OTP Collected as:  ======= " + map.get("Digits"));
			String otp = map.get("Digits");
			String encodedOTP = Hashing.sha256().hashString(otp, StandardCharsets.UTF_8).toString();
			otpToken = validateOTPTelephony(encodedOTP, otpTrxnId);
			otpToken = otpToken.replace("{\"token\":\"", "");
			otpToken = otpToken.replace("\"}", "");
			System.out.println("otpToken = " + otpToken);
			Thread.sleep(2000);
			Say getBenId;
			if("hi-IN".equalsIgnoreCase(languageSelected)) {
				getBenId = new Say.Builder("आपका O T P सही था। अब इसे सत्यापित करने के लिए आप, अब कृपया मुझे अपनी 14 अंकों की beneficiary id अपने मोबाइल पर दबाएं").voice(Say.Voice.POLLY_ADITI).build();} 
			else {
				getBenId = new Say.Builder("your O T P was valid. Now to verify its you, Please enter your 14 digit kowin beneficiary id one digit at a time").voice(Say.Voice.POLLY_ADITI).build();}
			Gather gatherBeneId = new Gather.Builder().timeout(10).numDigits(14).action("/download-cert")
					.inputs(Arrays.asList(Gather.Input.SPEECH, Gather.Input.DTMF)).say(getBenId).build();
			
			twiml= new VoiceResponse.Builder().gather(gatherBeneId).build();
			return twiml.toXml();

		});
		
		// 6. Download Cert File and send
		post("download-cert", (req, res) -> {

			Map<String, String> map = asMap(req.body(), "UTF-8");
			System.out.println("6. In Download Certificate, Beneficiary Id collected as: ======= " + map.get("Digits"));
			String benId = map.get("Digits");
			downloadCertificate(benId, otpToken);
			Thread.sleep(1000);
			
			String ACCOUNT_SID = "AC84040e7fc899fc50296d5f922731a64e";//Twilio Account - Account SID
			String AUTH_TOKEN = "56fe2a4996eda59b99337d67a85443bc";//Twilio Account - Auth Token
			Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
			String message = "";
			Say goodbye = new Say.Builder("your certificate is being sent on your whats app. Thank you for calling Co Voice bot. Good bye!").voice(Say.Voice.POLLY_ADITI).build();
			System.out.println("1.Preparing whatsapp messages");
			if("hi-IN".equalsIgnoreCase(languageSelected)) {
				message = "ये लिजिये आपका प्रमाणपत्";
				com.twilio.rest.api.v2010.account.Message.creator( 
						new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
						new com.twilio.type.PhoneNumber("whatsapp:+14155238886"),message).create();
				
				com.twilio.rest.api.v2010.account.Message.creator( 
						new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
						new com.twilio.type.PhoneNumber("whatsapp:+14155238886"), "Cert_" + benId + ".pdf").
						setMediaUrl(Arrays.asList(URI.create(ngrok_url + "Cert_" + benId + ".pdf"))).create();
				
				com.twilio.rest.api.v2010.account.Message.creator( 
						new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
						new com.twilio.type.PhoneNumber("whatsapp:+14155238886"), "धन्यवाद||").
						setMediaUrl(Arrays.asList(URI.create(ngrok_url  + "thankyou.jpeg"))).create();
				goodbye = new Say.Builder("आपका प्रमाणपत्र आपके whats ऐप पर भेजा गया है. Co Voice bot को कॉल करने के लिए धन्यवाद और अलविदा. आपका दिन मंगलमय हो.").voice(Say.Voice.POLLY_ADITI).build();
				}
			else {
				System.out.println("2 Preparing whatsapp messages");
				message = "Your Vaccine Certificate is here";
				com.twilio.rest.api.v2010.account.Message.creator( 
						new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
						new com.twilio.type.PhoneNumber("whatsapp:+14155238886"),message).create();
				
				com.twilio.rest.api.v2010.account.Message.creator( 
						new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
						new com.twilio.type.PhoneNumber("whatsapp:+14155238886"), "Cert_" + benId + ".pdf").
						setMediaUrl(Arrays.asList(URI.create(ngrok_url + "Cert_" + benId + ".pdf"))).create();
				
				com.twilio.rest.api.v2010.account.Message.creator( 
						new com.twilio.type.PhoneNumber("whatsapp:+91"+userPhoneNumber), 
						new com.twilio.type.PhoneNumber("whatsapp:+14155238886"),"THANK YOU!").
				setMediaUrl(Arrays.asList(URI.create(ngrok_url  + "thankyou.jpeg"))).create();
			}
			System.out.println("3 Preparing whatsapp messages");
			System.out.println("7. User Interaction ended with CoVoice Bot now.");
			twiml = new VoiceResponse.Builder().say(goodbye).build();
			return twiml.toXml();
		});
	}
	//this method help in calling COWIN API to send out an OTP to user registered mobile number with COWIN portal
	public static String generateOTPTelephony(String mobile) {

		System.out.println("4.a.Inside GenerateOTP API Call :: User Mobile Number == " + mobile);
		mobile = "{\"mobile\": \"" + mobile + "\"}";
		String url = "https://cdn-api.co-vin.in/api/v2/auth/public/generateOTP";

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		List<MediaType> list = new ArrayList<MediaType>();

		list.add(MediaType.ALL);
		headers.setAccept(list);
		HttpEntity<String> requestEntity = new HttpEntity<String>(mobile, headers);
		//System.out.println("COWIN Generate OTP API Request Payload == " + requestEntity.getBody());
		ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
		//System.out.println("COWIN Generate OTP API Response Payload == " +response);

		return response.getBody();
	}
	//This method helps in calling COWIN API for validating user entered OTP 
	public static String validateOTPTelephony(String encodedOTP, String trxnId) {

		String cowin_confirmotp_url = "https://cdn-api.co-vin.in/api/v2/auth/public/confirmOTP";
		System.out.println("5.a. Inside ValidateOTP API Call:");
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		List<MediaType> list = new ArrayList<MediaType>();
		list.add(MediaType.ALL);
		headers.setAccept(list);

		ConfirmOTPRequest confirmOTPRequest = new ConfirmOTPRequest();
		confirmOTPRequest.setOtp(encodedOTP);
		confirmOTPRequest.setTxnId(trxnId);

		HttpEntity<ConfirmOTPRequest> requestEntity = new HttpEntity<ConfirmOTPRequest>(confirmOTPRequest, headers);
		//System.out.println("COWIN  ConfirmOTP API RequestEntity Payload== " + requestEntity.getBody());
		ResponseEntity<String> response = restTemplate.exchange(cowin_confirmotp_url, HttpMethod.POST, requestEntity, String.class);
		//System.out.println("COWIN  ConfirmOTP API Response Payload== " +response);

		return response.getBody();

	}

	// FINAL DOWNLOAD Method
	//this method helps to call COWIN API to fetch vaccination certificate, and convert to pdf and save in a particular location, 
	//the location would be under target/classes path, to make it work. This can be changed as per needs.
	public static void downloadCertificate(String benRefId, String token) throws Exception {

		System.out.println("6.a. Inside DownloadCertificate :: Bearer token == " + token);
		String cowin_url_getcertificate = "https://cdn-api.co-vin.in/api/v2/registration/certificate/public/download?beneficiary_reference_id=" + benRefId;
		System.out.println("COWIN URL Formed == " + cowin_url_getcertificate);
		HttpHeaders headers = new HttpHeaders();
		List<MediaType> list = new ArrayList<MediaType>();
		list.add(MediaType.APPLICATION_PDF);
		headers.setAccept(list);
		headers.add("Authorization", "Bearer "+token);

		HttpEntity<ConfirmOTPRequest> requestEntity = new HttpEntity<ConfirmOTPRequest>(headers);
		Thread.sleep(2000);
		restTemplate = restTbc.build();
		ResponseEntity<byte[]> response = restTemplate.exchange(cowin_url_getcertificate, HttpMethod.GET, requestEntity, byte[].class);
		// Writes file directly under context (Main project folder)
		Files.write(Paths.get(System.getProperty("user.dir") + "/Cert_" + benRefId + ".pdf"), response.getBody());
		System.out.println("Certificate File Copy Started..");
		File src = new File(System.getProperty("user.dir") + "/Cert_" + benRefId + ".pdf");
		System.out.println("Certificate SRC file: " + src.getAbsolutePath());

		File dest = new File(System.getProperty("user.dir") + "/target/classes/static/Cert_" + benRefId + ".pdf");
		System.out.println("Certificate DEST file: " + dest.getAbsolutePath());

		FileUtils.copyFile(src, dest);
		System.out.println("Certificate File copied? " + Files.exists(Paths.get(System.getProperty("user.dir") + "/target/classes/static/Cert_" + benRefId + ".pdf")));
		System.out.println("Certificate File Copy Completed..");
		FileUtils.forceDelete(src);
		System.out.println("Certificate SRC file deleted? " + Files.exists(Paths.get(System.getProperty("user.dir") + "/Cert_" + benRefId + ".pdf")));

	}
	// END
	
	// - change the audio file path save location 
	//The audio content recorded from user are saved using this utility method in wav format
	public static void saveAudioFile(String recordingUrl) {
		System.out.println("saveAudioFile started");
		URLConnection conn;
		try {
			conn = new URL(recordingUrl).openConnection();
			InputStream is = conn.getInputStream();
			//Change this to local path to save audio recordings before conversion
			OutputStream outstream = new FileOutputStream(new File("/Users/****/Downloads/twilioRecordings/twilio.wav"));
			byte[] buffer = new byte[4096];
			int len;
			while ((len = is.read(buffer)) > 0) {
				outstream.write(buffer, 0, len);
			}
			outstream.close();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("saveAudioFile done");
	}
	// - change the audio file save path
	//Utility method to convert audio file as per DetectIntentStream api requirements.
	private static String convertFile() {
		System.out.println("convertFile started");
		AudioInputStream stream;
		File file = new File("/Users/*****/Downloads/twilioRecordings/twilioconverted.wav");
		try {
			stream = AudioSystem.getAudioInputStream(new File("/Users/****/Downloads/twilioRecordings/twilio.wav"));
			AudioFormat sourceFormat = stream.getFormat();
			AudioFormat targetFormat = new AudioFormat(sourceFormat.getEncoding(),16000,
					sourceFormat.getSampleSizeInBits(),sourceFormat.getChannels(),
					sourceFormat.getFrameSize(),sourceFormat.getFrameRate(),sourceFormat.isBigEndian());
			AudioInputStream ais = AudioSystem.getAudioInputStream(targetFormat, stream);
			AudioSystem.write(ais, Type.WAVE, file);

		} catch (UnsupportedAudioFileException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("convertFile completed");
		return file.getAbsolutePath();
	}
	//utility method to parse api response
	public static Map<String, String> asMap(String urlencoded, String encoding) throws UnsupportedEncodingException {

		Map<String, String> map = new LinkedHashMap<>();
		for (String keyValue : urlencoded.trim().split("&")) {

			String[] tokens = keyValue.trim().split("=");
			String key = tokens[0];
			String value = tokens.length == 1 ? null : URLDecoder.decode(tokens[1], encoding);
			map.put(key, value);
		}
		return map;
	}

}

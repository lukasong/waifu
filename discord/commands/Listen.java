package discord.commands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import discord.audio.AudioPlayerSendHandler;
import discord.audio.PlayerManager;
import discord.audio.TrackScheduler;
import discord.handler.Backend;
import discord.handler.Frontend;
import discord.handler.Variables;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.sound.midi.Track;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static discord.handler.Backend.funcRotateAgents;

public class Listen {

    //public variables
    public static String strCurrentSong = "null";
    public static String strCurrentArtist = "null";
    public static String strCurrentPlatform = "null";
    public static String strCurrentPlatformID = "null";
    public static boolean boolCurrentlyPlaying = false;
    public static int intRetries = 0;
    public static Timer timer;
    PlayerManager manager = PlayerManager.getInstance();
    public static ArrayList<String> arrayYoutubeTitleFilter = new ArrayList<String>();
    public static ArrayList<String> arrayYoutubeHREFilter = new ArrayList<String>();
    public static ArrayList<String> arraySoundcloudTitleFilter = new ArrayList<>();

    public Listen(boolean inputType) {
        if(inputType == true) {
            Backend.logme(5, "reached");
            Backend.logme(6, "attempting to create a ListenAlong session!");
            //join the channel
            boolean boolContinue = true;
            try {
                Frontend.funcJoinChannel(Variables.guildRaw.getMemberById(Variables.senderId).getVoiceState().getChannel());
            }catch(Exception e){
                boolContinue = false;
                Frontend.funcMessageError("u arent even in a voice channel lol, join one");
            }
            if(boolContinue == true){
                //creating a status
                Frontend.funcUpdatePresence("listening", "music w/ " + Variables.senderRaw.getName());
                //creating the thread
                timer = new Timer();
                timer.schedule(new UpdateListen(), 0, 10000);
                //sending a message
                Helpers.funcSendMesssage("<a:jamming:711326481799970878> ListenAlong session created, following " + Variables.senderRaw.getName() + " uwu", "startlistenalong", true);
            }
        }else{ //leaving
            if(boolCurrentlyPlaying == true) {
                try {
                    TrackScheduler.nextTrack();
                } catch (Exception e) {
                    Backend.logme(6, "cant skip the track sooo just playing track");
                }
                timer.purge();
                Helpers.funcSendMesssage("ListenAlong session ended! :(", "endlistenalong", true);
                Frontend.funcUpdatePresence("clear", "wtv");
                Frontend.funcLeaveChannel();
            }else{
                Frontend.funcMessageError("A ListenAlong party isn't even created?????");
            }
        }
    }

    public void ListenRetry(){
        try{
            timer.purge();
        }catch(Exception e){

        }
        timer = new Timer();
        timer.schedule(new UpdateListen(), 0, 10000);
    }

    public static String funcGetMusicStatus(boolean inputGet /* false is song true is artist */) {
        try {
            String strAllActivities = Variables.guildRaw.getMemberById(Variables.senderId).getActivities().toString();
            if(strAllActivities.contains("607697998490894356")){
                strCurrentPlatform = "soundcloud";
                strCurrentPlatformID = "607697998490894356";
            }else if(strAllActivities.contains("463151177836658699")){
                strCurrentPlatform = "youtube";
                strCurrentPlatformID = "463151177836658699";
            }
            int intActivitySize = Variables.guildRaw.getMemberById(Variables.senderId).getActivities().size();
            int intSelectedIndex = -1;
            for (int i = 0; i < intActivitySize; i++) {
                String strThisTest = Variables.guildRaw.getMemberById(Variables.senderId).getActivities().get(i).toString();
                if (strThisTest.contains(strCurrentPlatformID)){
                    intSelectedIndex = i;
                    i = intActivitySize;
                }
            }
            if (!(intSelectedIndex == -1)) {
                if(inputGet == false){
                    //return song
                    VoiceChannel thisChannel = Variables.guildRaw.getMemberById(Variables.senderId).getVoiceState().getChannel();
                    return Variables.guildRaw.getMemberById(Variables.senderId).getActivities()
                            .get(intSelectedIndex).asRichPresence().getDetails();
                }else{
                    //return artist
                    return  Variables.guildRaw.getMemberById(Variables.senderId).getActivities()
                            .get(intSelectedIndex).asRichPresence().getState();
                }
            } else {
                //discord.handler.Frontend.funcMessageError("u dont seem to have a utube music status..");
                return "nostatusdetected";
            }
        } catch (Exception e) {
            //error here eventually?
            //discord.handler.Frontend.funcMessageError("i couldnt grab your status.. are you doing anything? are you offline? im too lazy to check myself..");
            return "nostatusdetected";
        }
    }

    public static String funcGetMusicLink(String inputType, String inputName, String inputArtist) throws IOException {
        if ((!inputName.equals("null")) && (!inputArtist.equals("null"))) {
            switch (inputType) {
                case "youtube":
                    //filtering out the search query
                    try {
                        Backend.logme(5, "Filtered out the album!");
                        inputArtist = inputArtist.substring(0, inputArtist.indexOf("-"));
                    } catch (Exception e) {
                        Backend.logme(5, "Attempt to filter out the album failed!");
                    }
                    String strSearchFilter = (inputName + " " + inputArtist)
                            .replaceAll(" ", "+").replaceAll(",", "%2C")
                            .replaceAll("&", "and").replaceAll("\\(", "").replaceAll("\\)", "");
                    //connecting, DON'T sort by popularity as of now
                    String strURL = "https://www.youtube.com/results?search_query=" + strSearchFilter + ""; //&sp=CAM%253D
                    Backend.logme(5, "imma search for " + strURL + " on " + inputType + "!");
                    Document docYoutube = Jsoup.connect(strURL).userAgent(funcRotateAgents()).get();
                    //building the filter
                    funcBuildFilter("youtube");
                    //getting the link i want
                    try {
                        String strTitle = "null";
                        String strLink = "null";
                        for (int i = 0; i < docYoutube.getElementsByTag("a").size(); i++) {
                            strTitle = docYoutube.getElementsByTag("a").get(i).attributes().get("class");
                            strLink = docYoutube.getElementsByTag("a").get(i).attributes().get("href");
                            if (strTitle.contains("yt-uix-tile-link")) {
                                strTitle = docYoutube.getElementsByTag("a").get(i).attributes().get("title");
                                //going through filter
                                boolean boolFlagged = false;
                                for (int y = 0; y < arrayYoutubeTitleFilter.size(); y++) {
                                    if (strTitle.toLowerCase().contains(arrayYoutubeTitleFilter.get(y))) {
                                        //for the sake of my sanity, nested and not combined
                                        if (!(inputName + " " + inputArtist).toLowerCase().contains(arrayYoutubeTitleFilter.get(y))) {
                                            boolFlagged = true;
                                        }
                                    }
                                }
                                for (int y = 0; y < arrayYoutubeHREFilter.size(); y++) {
                                    if (strLink.toLowerCase().contains(arrayYoutubeHREFilter.get(y))) {
                                        boolFlagged = true;
                                    }
                                }
                                if (boolFlagged == false) {
                                    strLink = docYoutube.getElementsByTag("a").get(i).attributes().get("href");
                                    i = docYoutube.getElementsByTag("a").size() + 1;
                                }
                            }
                        }
                        //constructing link
                        strTitle = "https://www.youtube.com" + strLink;
                        return strTitle;
                        //Helpers.funcSendMesssage(strTitle, "test", true);
                    } catch (Exception e) {
                        Backend.logme(5, "i couldnt find a url for the following request: " + inputType + " / " + inputName + " / " + inputArtist);
                        Backend.logme(5, e.toString());
                        return "null";
                    }
                case "soundcloud":
                    String strSCFilteredQuary = (inputName + " " + inputArtist)
                            .replaceAll(" ", "+").replaceAll(",", "%2C")
                            .replaceAll("&", "and").replaceAll("\\(", "").replaceAll("\\)", "")
                            .replaceAll("'", "").replaceAll("\\[", "").replaceAll("\\]", "")
                            .replaceAll("❤", "").replaceAll("Ø", "o").replaceAll("™", "");
                    String strSCURL = "https://soundcloud.com/search?q=" + strSCFilteredQuary + "";
                    Backend.logme(5, "imma search for " + strSCURL + " on " + inputType + "!");
                    Document docSoundcloud = Jsoup.connect(strSCURL).userAgent(funcRotateAgents()).get();
                    funcBuildFilter("soundcloud");
                    try {
                        String strTitle = "null";
                        String strLink = "null";
                        for (int i = 0; i < docSoundcloud.getElementsByTag("a").size(); i++) {
                            strTitle = docSoundcloud.getElementsByTag("a").get(i).toString();
                            strLink = docSoundcloud.getElementsByTag("a").get(i).attributes().get("href");
                            boolean boolFlagged = false;
                            for (int y = 0; y < arraySoundcloudTitleFilter.size(); y++) {
                                if (strLink.toLowerCase().equals(arraySoundcloudTitleFilter.get(y))) {
                                    boolFlagged = true;
                                }
                            }
                            if (boolFlagged == false) {
                                strLink = docSoundcloud.getElementsByTag("a").get(i).attributes().get("href");
                                i = docSoundcloud.getElementsByTag("a").size() + 1;
                            }
                        }
                        //constructing link
                        strTitle = "https://soundcloud.com" + strLink;
                        Backend.logme(5, "Sending: " + strTitle);
                        return strTitle;
                    }catch(Exception e){
                        Backend.logme(5, "i couldnt find a url for the following request: " + inputType + " / " + inputName + " / " + inputArtist);
                        Backend.logme(5, e.toString());
                        return "null";
                    }
                default:
                    return "null";
            }
        } else {
            //Frontend.funcMessageError("pwease wait a second before requesting again!");
            return "null";
        }
    }

    public static void funcBuildFilter(String inputFilter) {
        switch (inputFilter) {
            case "youtube":
                arrayYoutubeTitleFilter.clear();
                arrayYoutubeTitleFilter.add("music video");
                arrayYoutubeTitleFilter.add("live");
                arrayYoutubeTitleFilter.add("dance video");
                arrayYoutubeTitleFilter.add("reaction");
                arrayYoutubeTitleFilter.add("full album");
                arrayYoutubeHREFilter.clear();
                arrayYoutubeHREFilter.add("googleadservices");
                arrayYoutubeHREFilter.add("&list=");
                break;
            case "soundcloud":
                arraySoundcloudTitleFilter.clear();
                arraySoundcloudTitleFilter.add("");
                arraySoundcloudTitleFilter.add(" ");
                arraySoundcloudTitleFilter.add("/popular/searches");
                arraySoundcloudTitleFilter.add("/search");
                arraySoundcloudTitleFilter.add("/search/sounds");
                arraySoundcloudTitleFilter.add("/search/sets");
                arraySoundcloudTitleFilter.add("/search/people");
                arraySoundcloudTitleFilter.add("https://help.soundcloud.com/hc/articles/115003564308-Technical-requirements");
                arraySoundcloudTitleFilter.add("http://google.com/chrome");
                arraySoundcloudTitleFilter.add("http://firefox.com");
                arraySoundcloudTitleFilter.add("http://apple.com/safari");
                arraySoundcloudTitleFilter.add("http://windows.microsoft.com/ie");
                arraySoundcloudTitleFilter.add("https://help.soundcloud.com");
                arraySoundcloudTitleFilter.add("https://soundcloud.com/popular/searches");
                arraySoundcloudTitleFilter.add("https://soundcloud.com/");
                arraySoundcloudTitleFilter.add("/");
                arraySoundcloudTitleFilter.add("http://www.enable-javascript.com/");
                arraySoundcloudTitleFilter.add("https://soundcloud.comhttps://help.soundcloud.com/hc/articles/115003564308-Technical-requirements");
            default:
                break;
        }
    }

    class UpdateListen extends TimerTask {
        public void run() {
            //check presence and see if updated
            String strThisSong = funcGetMusicStatus(false);
            String strThisArtist = funcGetMusicStatus(true);
            //Backend.logme(5, strThisArtist + strThisSong);
            try {
                if (strThisArtist.equals("null") || strThisArtist.isEmpty()) {
                    strThisArtist = "nostatusdetected";
                }
                if (strThisSong.equals("null") || strThisSong.isEmpty()) {
                    strThisSong = "nostatusdetected";
                }
            }catch(Exception e){
                strThisArtist = "nostatusdetected";
                strThisSong = "nostatusdetected";
            }
            if(!(strThisSong.equals("nostatusdetected")) && !(strThisArtist.equals("nostatusdetected"))) {
                if(!strCurrentSong.equals(strThisSong)){
                    strCurrentSong = strThisSong;
                    strCurrentArtist = strThisArtist;
                    String strThisLink = "null";
                    try {
                        strThisLink = funcGetMusicLink(strCurrentPlatform, strThisSong, strThisArtist);
                    } catch (IOException e) {
                        Backend.logme(6, "couldnt get music link.. oof");
                    }
                    try {
                        TrackScheduler.nextTrack();
                    }catch(Exception e){
                        Backend.logme(6, "cant skip the track sooo just playing track");
                    }
                    manager.loadAndPlay(Variables.textchannelRaw, strThisLink);
                    manager.getGuildMusicManager(Variables.guildRaw).player.setVolume(100);
                    boolCurrentlyPlaying = true;
                }else{
                    Backend.logme(6, "no new song to update!");
                }
            }else{
                Backend.logme(6, "uhhh what do i do now i just cant get their status lol");
                Frontend.funcMessageError("You don't have a Youtube Music status!");
            }
        }
    }

}

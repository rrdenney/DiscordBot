package discordbot.command.music;

import com.google.common.base.Joiner;
import discordbot.command.CommandVisibility;
import discordbot.core.AbstractCommand;
import discordbot.db.model.OMusic;
import discordbot.db.table.TMusic;
import discordbot.handler.MusicPlayerHandler;
import discordbot.handler.Template;
import discordbot.main.Config;
import discordbot.main.DiscordBot;
import discordbot.util.YTSearch;
import discordbot.util.YTUtil;
import net.dv8tion.jda.entities.*;

import java.io.File;

/**
 * !play
 * plays a youtube link
 * yea.. play is probably not a good name at the moment
 */
public class Play extends AbstractCommand {
	YTSearch ytSearch;

	public Play(DiscordBot b) {
		super(b);
		ytSearch = new YTSearch(Config.GOOGLE_API_KEY);
	}

	@Override
	public String getDescription() {
		return "Plays a song from youtube";
	}

	@Override
	public String getCommand() {
		return "play";
	}

	@Override
	public CommandVisibility getVisibility() {
		return CommandVisibility.PUBLIC;
	}

	@Override
	public String[] getUsage() {
		return new String[]{
				"play <youtubelink>    //download and plays song",
				"play <part of title>  //shows search results",
				"play                  //just start playing something"
		};
	}

	@Override
	public String[] getAliases() {
		return new String[0];
	}

	private boolean isInVoiceWith(Guild guild, User author) {
		VoiceChannel channel = guild.getVoiceStatusOfUser(author).getChannel();
		for (User user : channel.getUsers()) {
			if (user.getId().equals(bot.client.getSelfInfo().getId())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String execute(String[] args, MessageChannel channel, User author) {
		TextChannel txt = (TextChannel) channel;
		Guild guild = txt.getGuild();
		if (!isInVoiceWith(guild, author)) {
			bot.connectTo(guild.getVoiceStatusOfUser(author).getChannel());
			try {
				Thread.sleep(2000L);// ¯\_(ツ)_/¯
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (bot.isConnectedTo(guild.getVoiceStatusOfUser(author).getChannel())) {
				return "can't connect to you";
			}
		} else if (MusicPlayerHandler.getFor(guild, bot).getUsersInVoiceChannel().size() == 0) {
			return Template.get("music_no_users_in_channel");
		}
		if (args.length > 0) {

			String videocode = YTUtil.extractCodeFromUrl(args[0]);
			if (!YTUtil.isValidYoutubeCode(videocode)) {
				videocode = ytSearch.getResults(Joiner.on(" ").join(args));
			}
			if (YTUtil.isValidYoutubeCode(videocode)) {

				final File filecheck = new File(YTUtil.getOutputPath(videocode));
				if (!filecheck.exists()) {
					String finalVideocode = videocode;
					bot.out.sendAsyncMessage(channel, Template.get("music_downloading_hang_on"), message -> {
						System.out.println("starting download with code:::::" + finalVideocode);
						if (YTUtil.downloadfromYoutubeAsMp3(finalVideocode)) {
//							message.updateMessageAsync(Template.get("music_resampling"), null);
//							YTUtil.resampleToWav(finalVideocode);
						}
						if (filecheck.exists()) {
							OMusic rec = TMusic.findByYoutubeId(finalVideocode);
							rec.youtubeTitle = YTUtil.getTitleFromPage(finalVideocode);
							rec.youtubecode = finalVideocode;
							rec.filename = filecheck.getAbsolutePath();
							TMusic.update(rec);
							bot.addSongToQueue(filecheck.getAbsolutePath(), guild);
							message.updateMessageAsync(":notes: Found *" + rec.youtubeTitle + "* And added it to the queue", null);
						} else {
							message.deleteMessage();
						}
					});
				} else if (filecheck.exists()) {
					bot.addSongToQueue(filecheck.getAbsolutePath(), guild);
					return Template.get("music_added_to_queue");
				}
			} else {

				return Template.get("command_play_no_results");

			}
		} else {
			if (bot.playRandomSong(guild)) {
				return Template.get("music_started_playing_random");
			} else {
				return Template.get("music_failed_to_start");
			}
		}
		return Template.get("music_not_added_to_queue");
	}
}
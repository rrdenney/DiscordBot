package discordbot.service;

import com.google.api.client.repackaged.com.google.common.base.Splitter;
import discordbot.core.AbstractService;
import discordbot.main.BotContainer;
import discordbot.main.Config;
import discordbot.main.DiscordBot;
import discordbot.modules.github.GitHub;
import discordbot.modules.github.GithubConstants;
import discordbot.modules.github.pojo.RepositoryCommit;
import discordbot.util.Misc;
import discordbot.util.TimeUtil;
import net.dv8tion.jda.entities.TextChannel;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * check for news on github
 */
public class GithubService extends AbstractService {

	private final static int MAX_COMMITS_PER_POST = 10;

	public GithubService(BotContainer b) {
		super(b);
	}

	@Override
	public String getIdentifier() {
		return "bot_code_updates";
	}

	@Override
	public long getDelayBetweenRuns() {
		return 900_000;
	}

	@Override
	public boolean shouldIRun() {
		return true;
	}

	@Override
	public void beforeRun() {
	}

	@Override
	public void run() {
		DiscordBot bot = this.bot.getShards()[0];
		String totalMessage;
		String commitsMessage = "";
		long lastKnownCommitTimestamp = Long.parseLong("0" + getData("last_date"));
		long newLastKnownCommitTimestamp = lastKnownCommitTimestamp;
		RepositoryCommit[] changesSinceHash = GitHub.getChangesSinceTimestamp("MaikWezinkhof", "discordbot", lastKnownCommitTimestamp);
		int commitCount = 0;//probably changesSinceHash.length - 1
		List<List<String>> tblContent = new ArrayList<>();
		for (int i = changesSinceHash.length - 1; i >= 0; i--) {
			List<String> tableRow = new ArrayList<>();
			RepositoryCommit commit = changesSinceHash[i];
			Long timestamp = 0L;
			try {
				timestamp = GithubConstants.githubDate.parse(commit.getCommit().getCommitterShort().getDate()).getTime();
			} catch (ParseException ignored) {
			}
			String message = commit.getCommit().getMessage();
			String committer = commit.getAuthor().getLogin();
			if (3026105 == commit.getAuthor().getId()) {
				committer = "Kaaz";
			}
			if (timestamp > lastKnownCommitTimestamp) {
				commitsMessage += commitOutputFormat(timestamp, message, committer, commit.getSha());
				newLastKnownCommitTimestamp = timestamp;
				commitCount++;
				if (commitCount >= MAX_COMMITS_PER_POST) {
					break;
				}
				tableRow.add(commit.getSha().substring(0, 7));
				tableRow.add(committer);
				tableRow.add(message);
				tblContent.add(tableRow);
			}
		}
		if (commitCount > 0) {
			if (commitCount == 1) {
				totalMessage = "There has been a commit to my code" + Config.EOL;
			} else {
				totalMessage = "There have been **" + commitCount + "** commits to my code " + Config.EOL;
			}
			if (commitCount <= 3) {
				totalMessage += commitsMessage;
			} else {
				totalMessage += Misc.makeAsciiTable(Arrays.asList("hash", "committer", "description"), tblContent, null);
			}
			for (TextChannel chan : getSubscribedChannels()) {
				sendTo(chan, totalMessage);
			}
		}
		saveData("last_date", newLastKnownCommitTimestamp);
	}

	@Override
	public void afterRun() {
	}

	private String commitOutputFormat(Long timestamp, String message, String committer, String sha) {
		String timeString = "";
		String ret = "";
		long localtimestamp = timestamp + 1000 * 60 * 60 * 2;//+2hours cheat
		if (System.currentTimeMillis() - localtimestamp > 1000 * 60 * 60 * 8) {//only when its 8h+
			timeString = " :clock3: " + TimeUtil.getRelativeTime(localtimestamp / 1000L);
		}
		Iterable<String> lines;
		if (!message.contains("\n") && message.length() > 80) {
			lines = Splitter.fixedLength(80).split(message);
		} else {
			lines = Arrays.asList(message.split("\\r?\\n", 0));
		}
		boolean first = true;
		for (String line : lines) {
			if (first) {
				first = false;
				ret = ":arrow_up: `" + sha.substring(0, 7) + "` " + timeString + ":pencil: `" + line + "`" + Config.EOL;
			} else {
				ret += ":arrow_upper_right: `.......` " + timeString + ":speech_balloon: `" + line + "`" + Config.EOL;
			}
		}
		return ret;
	}
}
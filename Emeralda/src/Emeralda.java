import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.filechooser.FileSystemView;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class Emeralda {

	private final static Logger LOGGER = Logger.getLogger(Emeralda.class.getName());

	private CloseableHttpResponse response = null;
	private HttpEntity entity = null;

	private final String steamApiKey = "8AD3F1AAE074D6A5AE06B43CBA8EFFE9";
	private String steamID64 = null;
	private String botCustomUrl = "CardExchange";
	private String myCustomUrl = null;

	private List<Game> games = null;
	private List<Game> resultGames = null;

	private boolean getGames() {
		LOGGER.log(Level.INFO, "Getting game list.");
		BufferedReader br = getContent("http://steamcommunity.com/id/" + myCustomUrl + "/games/?tab=all");

		String line = null;
		
		try {
			for (; (line = br.readLine()) != null && !line.equals("<script language=\"javascript\">"););
			line = br.readLine();
		} catch (IOException e) {
			closeInputStream();
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return false;
		}
		
		closeInputStream();
		
		String[] lines = line.split("}}];|},\\{|\\[\\{|\",\"logo\"|,\"name\":\"|\"appid\":");

		games = new LinkedList<Game>();

		for (int i = 2, j = 0; j < (lines.length - 1) / 4; i += 3, j++) {
			Game game = new Game();
			game.id = Integer.parseInt(lines[i++]);
			game.name = UTF(lines[i]).toString();
			games.add(game);
		}
		LOGGER.log(Level.INFO, "Total Games: " + games.size() + "\n");
		return true;
	}

	private boolean getMyCards() {
		LOGGER.log(Level.INFO, "Getting your cards.\n");
		boolean error = false;
		int current = 1;
		int total = games.size();
		for (int i = 0; i < games.size(); i++, current++) {
			Game game = games.get(i);
			LOGGER.log(Level.INFO, current + " / " + total);
			LOGGER.log(Level.INFO, game.name + " (" +  game.id + ")");
			BufferedReader br = getContent("http://steamcommunity.com/id/" + myCustomUrl + "/gamecards/" + game.id);

			game.myCardsCount = 0;
			game.totalCardsCount = 0;

			String line = null;
			try {
				for (int j = 0; (line = br.readLine()) != null && j < 10; j++) {
					if (line.contains("<title>Steam Community :: Steam Badges</title>")) {
						error = true;
					}
				}
			} catch (IOException e) {
				closeInputStream();
				LOGGER.log(Level.SEVERE, e.toString(), e);
				return false;
			}
			
			if (error) {
				LOGGER.log(Level.INFO, "Cards are not supported.\n");
				error = false;
				games.remove(i);
				i--;
				continue;
			}

			try {
				for (; (line = br.readLine()) != null;) {
					if (line.contains("badge_card_set_text_qty")) {
						String[] lines = line.split("<div class=\"badge_card_set_text_qty\">\\(|\\)</div>");
						game.myCardsCount += Integer.parseInt(lines[1]);
						game.totalCardsCount++;
					} else if (line.contains("game_card_unowned_border")) {
						game.totalCardsCount++;
					}
				}
				LOGGER.log(Level.INFO, "Your cards: " + game.myCardsCount + " / " + game.totalCardsCount + "\n");
			} catch (NumberFormatException e) {
				closeInputStream();
				LOGGER.log(Level.SEVERE, e.toString(), e);
				return false;
			} catch (IOException e) {
				closeInputStream();
				LOGGER.log(Level.SEVERE, e.toString(), e);
				return false;
			}
		}
		closeInputStream();
		return true;
	}

	private boolean getBotCards() {
		LOGGER.log(Level.INFO, "Getting bot's cards.\n");
		int current = 1;
		int total = games.size();
		for (int i = 0; i < games.size(); i++, current++) {
			Game game = games.get(i);
			LOGGER.log(Level.INFO, current + " / " + total);
			LOGGER.log(Level.INFO, game.name + " (" +  game.id + ")");
			BufferedReader br = getContent("http://steamcommunity.com/id/" + botCustomUrl + "/gamecards/" + game.id);

			game.botCardsCount = 0;

			String line = null;
			try {
				for (; (line = br.readLine()) != null;) {
					if (line.contains("badge_card_set_text_qty")) {
						String[] lines = line.split("<div class=\"badge_card_set_text_qty\">\\(|\\)</div>");
						game.botCardsCount += Integer.parseInt(lines[1]);
					}
				}
				LOGGER.log(Level.INFO, "Bot's cards: " + game.botCardsCount + " / " + game.totalCardsCount + "\n");
			} catch (NumberFormatException e) {
				closeInputStream();
				LOGGER.log(Level.SEVERE, e.toString(), e);
				return false;
			} catch (IOException e) {
				closeInputStream();
				LOGGER.log(Level.SEVERE, e.toString(), e);
				return false;
			}
		}
		closeInputStream();
		return true;
	}

	private void analyze() {
		LOGGER.log(Level.INFO, "Performing analysis.\n");
		resultGames = new ArrayList<Game>();

		for (int i = 0; i < games.size(); i++) {
			Game game = games.get(i);
			game.cardsAvailable = game.botCardsCount - game.totalCardsCount;
			game.cardsNeeded = game.totalCardsCount - game.myCardsCount;
			if (game.cardsAvailable >= game.cardsNeeded) {
				LOGGER.log(Level.INFO, game.name + " (" + game.id + ") - [" + game.cardsAvailable + " / " + game.cardsNeeded + "]");
				resultGames.add(game);
			}
		}
		System.out.println();
		LOGGER.log(Level.INFO, "Analysis completed.\n");
	}

	private boolean output() {
		LOGGER.log(Level.INFO, "Performing output.");
		File file = new File(FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath() + "\\results.txt");

		if (file.exists()) {
			file.delete();
		}

		try {
			file.createNewFile();
			BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

			for (int i = 0; i < resultGames.size(); i++) {
				Game game = resultGames.get(i);
				writer.write(game.name + " (" + game.id + ") - [" + game.cardsAvailable + " / " + game.cardsNeeded + "]");
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return false;
		}
		LOGGER.log(Level.INFO, "Results succesfully exported to the desktop.");
		return true;
	}

	public Emeralda() {
		
		LOGGER.setLevel(Level.ALL);
		LOGGER.setUseParentHandlers(false);

		ConsoleHandler handler = new ConsoleHandler();
		handler.setFormatter(new MyFormatter());
		LOGGER.addHandler(handler);

		/*if(!loadProfile()) {
			inputProfile();
			saveProfile();
		}*/

		if(!inputProfile()) {
			return;
		}
		
		if(!getGames()) {
			return;
		}
		if(!getMyCards()) {
			return;
		}
		
		if(!getBotCards()) {
			return;
		}
		
		analyze();
		
		if(!output()) {
			return;
		}
	}

	private boolean inputProfile() {
		for (;;) {
			System.out.print("Enter SteamID64: ");
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

			try {
				steamID64 = in.readLine();
				System.out.println();
				in.close();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.toString(), e);
				return false;
			}

			
			if (steamID64.length() == 17) {
				Long.parseLong(steamID64);
				BufferedReader br = getContent("http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=" + steamApiKey + "&steamids=" + steamID64);
				
				String line = null;
				try {
					for (; (line = br.readLine()) != null;) {
						if(line.contains("profileurl")) {
							myCustomUrl = line.split("/\",|/id/")[1];
							closeInputStream();
							return true;
						}
					}
					LOGGER.log(Level.INFO, "Wrong SteamID64.\n");
				} catch (IOException e) {
					closeInputStream();
					LOGGER.log(Level.SEVERE, e.toString(), e);
					return false;
				}
			} else {
				LOGGER.log(Level.INFO, "Wrong SteamID64.\n");
			}
		}
	}
	
	/*private boolean loadProfile() {
		LOGGER.log(Level.INFO, "Loading saved profile.");
		File file = new File("save.dat");

		if (!file.exists()) {
			LOGGER.log(Level.INFO, "No saved profile.\n");
			return false;
		}

		FileReader reader = null;
		try {
			reader = new FileReader(file);
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return false;
		}
		
		BufferedReader br = new BufferedReader(reader);
		
		try {
			steamID64 = br.readLine();
			br.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return false;
		}
		
		if (steamID64 == null) {
			file.delete();
			return false;
		}
		LOGGER.log(Level.INFO, "Saved profile loaded successfully.\n");
		return true;
	}

	private boolean inputProfile() {
		for (;;) {
			System.out.print("Enter SteamID64: ");
			BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

			try {
				steamID64 = in.readLine();
				System.out.println();
				in.close();
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, e.toString(), e);
				return false;
			}

			
			if (steamID64.length() == 17) {
				Long.parseLong(steamID64);
				BufferedReader br = getContent("http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=" + steamApiKey + "&steamids=" + steamID64);
				
				String line = null;
				try {
					for (; (line = br.readLine()) != null;) {
						if(line.contains("profileurl")) {
							myCustomUrl = line.split("/\",|/id/")[1];
							closeInputStream();
							return true;
						}
					}
					LOGGER.log(Level.INFO, "Wrong SteamID64.\n");
				} catch (IOException e) {
					closeInputStream();
					LOGGER.log(Level.SEVERE, e.toString(), e);
					return false;
				}
			} else {
				LOGGER.log(Level.INFO, "Wrong SteamID64.\n");
			}
		}
	}
	
	private boolean saveProfile() {
		LOGGER.log(Level.INFO, "Saving profile.");
		File file = new File("save.dat");

		try {
			if (!file.exists()) {
				file.createNewFile();
			}
			BufferedWriter writer = new BufferedWriter(new FileWriter(file, true));

			writer.write(steamID64);
			writer.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return false;
		}
		LOGGER.log(Level.INFO, "Profile saved.\n");
		return true;
	}*/

	private BufferedReader getContent(String url) {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(url);

		try {
			response = httpclient.execute(httpGet);
			entity = response.getEntity();
			BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
			return br;
		} catch (ClientProtocolException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return null;
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return null;
		}


	}

	private boolean closeInputStream() {
		try {
			EntityUtils.consume(entity);
			response.close();
			return true;
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.toString(), e);
			return false;
		}

	}

	public StringBuffer UTF(String data) {
		Pattern p = Pattern.compile("\\\\u(\\p{XDigit}{4})");
		Matcher m = p.matcher(data);
		StringBuffer buf = new StringBuffer(data.length());
		
		for(; m.find();) {
			String ch = String.valueOf((char) Integer.parseInt(m.group(1), 16));
			m.appendReplacement(buf, Matcher.quoteReplacement(ch));
		}
		
		m.appendTail(buf);
		return buf;
	}

	public static void main(String[] args) {
		Emeralda main = new Emeralda();
	}
}
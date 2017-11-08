
public class Game {
	int id;
	String name;
	int totalCardsCount;
	int myCardsCount;
	int botCardsCount;
	
	int cardsAvailable;
	int cardsNeeded;
	
	public Game() {
		id = -1;
		name = null;
		totalCardsCount = -1;
		myCardsCount = -1;
		botCardsCount = -1;
		
		cardsAvailable = -1;
		cardsNeeded = -1;
	}
}

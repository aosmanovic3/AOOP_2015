package at.ac.tuwien.foop.server.service;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.ac.tuwien.foop.domain.Board.Field;
import at.ac.tuwien.foop.domain.Coordinates;
import at.ac.tuwien.foop.domain.Player;
import at.ac.tuwien.foop.domain.Player.State;
import at.ac.tuwien.foop.domain.Wind;
import at.ac.tuwien.foop.server.domain.BoardString;
import at.ac.tuwien.foop.server.domain.Game;

public class GameLogicService {

	//	private static String BOARD_PATH = "Map";
	private static Logger log = LoggerFactory.getLogger(GameLogicService.class);

	public BoardString loadBoard(String path) {

		//		log.debug("load board with path '{}'!", getBoardPath());
		//		log.info("Level: " + Game.getLevelCounter());
		try (Scanner s = new Scanner(Thread.currentThread()
				.getContextClassLoader()
				.getResourceAsStream(path))) {
			int width = 0;
			StringBuffer buffer = new StringBuffer();
			while (s.hasNextLine()) {
				String line = s.nextLine();
				if (width == 0)
					width = line.length();
				if (line.length() != width)
					throw new RuntimeException(String.format(
							"Board file '%s' has a bad format!", path));
				buffer.append(line);
			}

			return new BoardString(buffer.toString(), width);
		}
	}

	public void movement(Game game, Wind wind) {
		Coordinates cheeseCoordinates = game.board().cheeseCoordinates();
		log.debug("***calculate movement***");
		log.debug("Wind : "+  wind.toString());
		int windX = (int)wind.x;
		int windY = (int)wind.y;
		Field[][] fields = game.board().fields();


		for (Player player : game.getPlayers()) {
			log.info("Position of the mouse '{}': {}", player.name(),player.coordinates());

			// get neighbors with paths
			List<Coordinates> floorList = calculateNeighbor(fields, player.coordinates());

			// check for dead end
			if (floorList.size() == 1 && player.getLastCoordinates() != null) {
				player.setState(State.crazy);

				Coordinates c = addWindToCoordinates(player.getLastCoordinates(), windX, windY);
						if (checkIfPath(c,fields))
								game.movePlayer(player.name(), addWindToCoordinates(c, windX, windY));
						else
							game.movePlayer(player.name(), player.getLastCoordinates());

						continue;
			}

			// mouse crashes
			if (player.getState().equals(State.crash)) {
				if (player.crash()) {
					moveRandomlyDifferentDirections(player, game, floorList,wind);
				}
				continue;
			}



			// calculate player state changes and crazy movements
			if (!player.getState().equals(State.notCrazy)) {
				if (floorList.size() >= 3) {
					if (player.getState().equals(State.crazy)) {
						moveRandomly(player, game, floorList, wind);
						continue;
					} else
						player.setState(State.notCrazy);
				} else {
					Coordinates c = addWindToCoordinates(floorList
							.stream()
							.findFirst()
							.orElse(null), windX, windY);
					
					if (checkIfPath(c, fields) == true) 
						game.movePlayer(player.name(),c);
					else 
						game.movePlayer(player.name(),floorList
								.stream()
								.findFirst()
								.orElse(null));
					continue;
				}
			}

			// TODO: just a hack: removes last cordinates from possible
			// neighbors
			floorList = floorList.stream()
					.filter(z -> !z.equals(player.getLastCoordinates()))
					.collect(Collectors.toList());

			// calculate closest neighbor
			Coordinates closestNeighbor = calculateClosestNeighbor(floorList,cheeseCoordinates);

			
			Coordinates c = addWindToCoordinates(closestNeighbor, (int)wind.x, (int)wind.y);
			// check if path
			if (checkIfPath(c, fields) == true) {
				// move the player
				game.movePlayer(player.name(), c);
			} else 
				game.movePlayer(player.name(), closestNeighbor);
			
			
			// check if cheese was found
			if (closestNeighbor.equals(cheeseCoordinates)) {
				log.info("player '{}' wins the game!", player.name());
				game.stop(player);
				continue;
			}
			log.info("Last Coordinates of player '{}': {} ", player.name(),
					player.getLastCoordinates());
		}
		wind.setWindToDefault();
	}



	/**
	 * Calculate the closest neighbor to the cheese.
	 * 
	 * @param neighbors
	 * @param cheeseCoordinates
	 * @return
	 */
	private Coordinates calculateClosestNeighbor(List<Coordinates> neighbors,
			Coordinates cheeseCoordinates) {
		double minDistance = Double.POSITIVE_INFINITY;
		Coordinates closestNeighbor = null;
		for (Coordinates neighbor : neighbors) {
			double distance = calculateDistanceToCheese(cheeseCoordinates,
					neighbor);
			if (distance <= minDistance) {
				minDistance = distance;
				closestNeighbor = neighbor;
				log.info("Closest neighbor:" + closestNeighbor);
			}
		}
		return closestNeighbor;
	}

	/**
	 * Calculate neighbor fields of {@code position} that are not of type wall.
	 */
	private List<Coordinates> calculateNeighbor(Field[][] fields,
			Coordinates position) {
		int x = position.x;
		int y = position.y;

		return Arrays
				.asList(new Coordinates[] { new Coordinates(x, y - 1),
						new Coordinates(x, y + 1), new Coordinates(x - 1, y),
						new Coordinates(x + 1, y) })
						.stream()
						.filter(neighbour -> {
							if (neighbour.x < fields[0].length
									&& neighbour.x >= 0
									&& neighbour.y < fields.length
									&& neighbour.y >= 0
									&& !fields[neighbour.y][neighbour.x]
											.equals(Field.wall)) {
								return true;
							}
							return false;
						}).collect(Collectors.toList());
	}

	/**
	 * Calculate the distance between {@code position} and {@code cheese}.
	 */
	private double calculateDistanceToCheese(Coordinates position,
			Coordinates cheese) {
		double x = (cheese.x - position.x) * (cheese.x - position.x);
		double y = (cheese.y - position.y) * (cheese.y - position.y);
		return Math.sqrt(x + y);
	}

	public static String getBoardPath(int lvl) {
		return String.format("Map%d.txt", lvl);
	}

	//	public static void setBOARD_PATH(String bOARD_PATH) {
	//		BOARD_PATH = bOARD_PATH;
	//	}

	public Player checkCrash(Player player, Game game) {
		log.debug("***check crash of player '{}'***", player.name());
		int x = player.coordinates().x;
		int y = player.coordinates().y;

		for (Player p : game.getPlayers()) {
			if (p.equals(player)) {
				continue;
			}
			if (new Coordinates(x - 1, y).equals(p.coordinates())
					|| new Coordinates(x + 1, y).equals(p.coordinates())
					|| new Coordinates(x, y - 1).equals(p.coordinates())
					|| new Coordinates(x, y + 1).equals(p.coordinates())) {
				log.debug("crash found!");
				return p;
			}
		}
		return null;
	}

	public void moveRandomly(Player player, Game game, List<Coordinates> floorList, Wind wind) {
		player.setState(State.notSoCrazy);
		List<Coordinates> p = floorList.stream()
				.filter(z -> !z.equals(player.getLastCoordinates()))
				.collect(Collectors.toList());

		Coordinates c = addWindToCoordinates(p.get(new Random().nextInt(p.size())), (int)wind.x, (int)wind.y);
		
		if (checkIfPath(c, game.board().fields()) == true)
			game.movePlayer(player.name(), c);
		else 
			game.movePlayer(player.name(), p.get(new Random().nextInt(p.size())));

	}

	public void moveRandomlyDifferentDirections(Player player, Game game, List<Coordinates> floorList, Wind wind) {
		player.setState(State.notSoCrazy);
		List<Coordinates> p = floorList.stream()
				.filter(z -> !game.getPlayers().stream().anyMatch(pl -> pl.coordinates().equals(z)))
				.collect(Collectors.toList());
		log.debug("possible floors {}", p);

		Coordinates c = addWindToCoordinates(p.get(new Random().nextInt(p.size())), (int)wind.x, (int)wind.y);

		if (checkIfPath(c, game.board().fields()) == true)
			game.movePlayer(player.name(), c);
		else
			game.movePlayer(player.name(), p.get(new Random().nextInt(p.size())));


	}


	// add wind to the coordinates
	public Coordinates addWindToCoordinates(Coordinates coordinates, int x, int y) {
		
		Coordinates c = new Coordinates(coordinates.x+x,coordinates.y+y);
		
		
		
		return c;
	}


	private boolean checkIfPath(Coordinates c, Field[][] fields) {
		
		if (fields[c.y][c.x].equals(Field.floor)) 
			return true;
		else
			return false;
	}

}

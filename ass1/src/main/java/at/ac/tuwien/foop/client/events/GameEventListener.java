package at.ac.tuwien.foop.client.events;

@FunctionalInterface
public interface GameEventListener {
	public void update(GameEvent e);
}
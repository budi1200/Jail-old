package com.graywolf336.jail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import com.graywolf336.jail.beans.CachePrisoner;
import com.graywolf336.jail.beans.Cell;
import com.graywolf336.jail.beans.ConfirmPlayer;
import com.graywolf336.jail.beans.CreationPlayer;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.enums.Confirmation;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.steps.CellCreationSteps;
import com.graywolf336.jail.steps.JailCreationSteps;

/**
 * Handles all things related to jails.
 * 
 * <p>
 * 
 * Stores the following:
 * <ul>
 * 	<li>The {@link Jail jails}, which contains the prisoners and cells.</li>
 * 	<li>Players creating jails, see {@link CreationPlayer}.</li>
 * 	<li>Players creating jail cells, see {@link CreationPlayer}.</li>
 * 	<li>An instance of {@link JailCreationSteps} for stepping players through the Jail creation process.</li>
 * </ul>
 * 
 * @author graywolf336
 * @since 3.0.0
 * @version 1.1.0
 */
public class JailManager {
    private JailMain plugin;
    private HashMap<String, Jail> jails;
    private HashMap<String, CreationPlayer> jailCreators;
    private HashMap<String, CreationPlayer> cellCreators;
    private HashMap<String, ConfirmPlayer> confirms;
    private HashMap<UUID, CachePrisoner> cache;
    private JailCreationSteps jcs;
    private CellCreationSteps ccs;

    protected JailManager(JailMain plugin) {
        this.plugin = plugin;
        this.jails = new HashMap<String, Jail>();
        this.jailCreators = new HashMap<String, CreationPlayer>();
        this.cellCreators = new HashMap<String, CreationPlayer>();
        this.confirms = new HashMap<String, ConfirmPlayer>();
        this.cache = new HashMap<UUID, CachePrisoner>();
        this.jcs = new JailCreationSteps();
        this.ccs = new CellCreationSteps();
    }

    /**
     * Returns the instance of the plugin main class.
     * 
     * @return {@link JailMain} instance
     */
    public JailMain getPlugin() {
        return this.plugin;
    }

    /**
     * Returns a HashSet of all the jails.
     * 
     * @return HashSet of all the jail instances.
     */
    public HashSet<Jail> getJails() {
        return new HashSet<Jail>(jails.values());
    }

    /**
     * Returns an array of all the names of the jails.
     * 
     * @return Array of the jail names
     */
    public String[] getJailNames() {
        String[] toReturn = new String[jails.size()];
        
        int count = 0;
        for(Jail j : this.jails.values()) {
            toReturn[count] = j.getName();
            count++;
        }
        
        return toReturn;
    }
    
    /**
     * Gets a list of Jail names that start with the provided prefix.
     * 
     * <p>
     * 
     * If the provided prefix is empty, then we add all of the jails.
     * 
     * @param prefix The start of the jails to get
     * @return List of jails that matched the prefix
     */
    public List<String> getJailsByPrefix(String prefix) {
        List<String> results = new ArrayList<String>();
        
        for(Jail j : this.jails.values())
            if(prefix.isEmpty() || StringUtil.startsWithIgnoreCase(j.getName(), prefix))
                results.add(j.getName());
        
        Collections.sort(results);
        
        return results;
    }

    /**
     * Adds a jail to the collection of them.
     * 
     * @param jail The jail to add
     * @param n True if this is a new jail, false if it isn't.
     */
    public void addJail(Jail jail, boolean n) {
        this.jails.put(jail.getName().toLowerCase(), jail);
        if(n) plugin.getJailIO().saveJail(jail);
    }

    /**
     * Removes a {@link Jail}.
     * 
     * @param name of the jail to remove
     */
    public void removeJail(String name) {
        plugin.getJailIO().removeJail(this.jails.get(name.toLowerCase()));
        this.jails.remove(name.toLowerCase());
    }

    /**
     * Gets a jail by the given name.
     * 
     * @param name The name of the jail to get.
     * @return The {@link Jail} with the given name, if no jail found this <strong>will</strong> return null.
     */
    public Jail getJail(String name) {
        if(name.isEmpty() && jails.isEmpty())
            return null;
        else
            return name.isEmpty() ? this.jails.values().iterator().next() : this.jails.get(name.toLowerCase());
    }

    /**
     * Gets the nearest {@link Jail} to the player, if the sender is a player or else it will get the first jail defined.
     * 
     * @param sender The sender who we are looking around.
     * @return The nearest {@link Jail} to the sender if it is a player or else the first jail defined.
     */
    public Jail getNearestJail(CommandSender sender) {
        if(jails.isEmpty()) return null;
        
        if(sender instanceof Player) {
            Location loc = ((Player) sender).getLocation();

            Jail j = null;
            double len = -1;

            for(Jail jail : jails.values()) {
                double clen = jail.getDistance(loc);

                if (clen < len || len == -1) {
                    len = clen;
                    j = jail;
                }
            }

            return (j == null ? jails.values().iterator().next() : j);
        }else {
            return jails.values().iterator().next();
        }
    }

    /**
     * Gets the jail which this location is in, will return null if none exist.
     * 
     * @param loc to get the jail from
     * @return The jail this block is in, null if no jail found.
     */
    public Jail getJailFromLocation(Location loc) {
        for(Jail j : jails.values()) {
            if(Util.isInsideAB(loc.toVector(), j.getMinPoint().toVector(), j.getMaxPoint().toVector()) && loc.getWorld() == j.getWorld()) {
                return j;
            }
        }

        return null;
    }

    /**
     * Gets whether the location is inside of a Jail.
     * 
     * @param l to determine if is in a jail
     * @return whether it is inside a jail or not
     */
    public boolean isLocationAJail(Location l) {
        for(Jail j : jails.values()) {
            if(Util.isInsideAB(l.toVector(), j.getMinPoint().toVector(), j.getMaxPoint().toVector()) && l.getWorld() == j.getWorld()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks to see if the given name for a {@link Jail} is valid, returns true if it is a valid jail.
     * 
     * @param name The name of the jail to check.
     * @return True if a valid jail was found, false if no jail was found.
     */
    public boolean isValidJail(String name) {
        return this.jails.containsKey(name.toLowerCase());
    }

    /**
     * Gets all the {@link Cell cells} in the jail system, best for system wide count of the cells or touching each cell.
     * 
     * @return HashSet of all the Cells.
     */
    public HashSet<Cell> getAllCells() {
        HashSet<Cell> cells = new HashSet<Cell>();

        for(Jail j : jails.values())
            cells.addAll(j.getCells());

        return cells;
    }

    /**
     * Adds a prisoner to the cache.
     * 
     * @param cache object to store
     * @return The same object given
     */
    public CachePrisoner addCacheObject(CachePrisoner cache) {
        plugin.debug("Adding " + cache.getPrisoner().getUUID().toString() + " to the cache.");
        this.cache.put(cache.getPrisoner().getUUID(), cache);
        return this.cache.get(cache.getPrisoner().getUUID());
    }

    /**
     * Checks if the given uuid is in the cache.
     * 
     * @param uuid of the player
     * @return true if in cache, false if not
     */
    public boolean inCache(UUID uuid) {
        return this.cache.containsKey(uuid);
    }

    /**
     * Gets a cached prisoner object.
     * 
     * @param uuid of the prisoner to get
     * @return the cahced prisoner object, will be null if it doesn't exist
     */
    public CachePrisoner getCacheObject(UUID uuid) {
        return this.cache.get(uuid);
    }

    /**
     * Removes the cache object stored for this uuid.
     * 
     * @param uuid of the prisoner to remove
     */
    public void removeCacheObject(UUID uuid) {
        plugin.debug("Removing " + uuid.toString() + " from the cache.");
        this.cache.remove(uuid);
    }

    /**
     * Gets all the prisoners in the system, best for a system wide count of the prisoners or accessing all the prisoners at once.
     * 
     * @return HashSet of Prisoners.
     */
    public HashMap<UUID, Prisoner> getAllPrisoners() {
        HashMap<UUID, Prisoner> prisoners = new HashMap<UUID, Prisoner>();

        for(Jail j : jails.values())
            prisoners.putAll(j.getAllPrisoners());

        return prisoners;
    }

    /**
     * Gets the {@link Jail jail} the given prisoner is in.
     * 
     * @param prisoner The prisoner data for the prisoner we are checking
     * @return The jail the player is in, <strong>CAN BE NULL</strong>.
     */
    public Jail getJailPrisonerIsIn(Prisoner prisoner) {
        if(prisoner == null) return null;
        else return getJailPlayerIsIn(prisoner.getUUID());
    }

    /**
     * Gets the {@link Jail jail} the given player is in.
     * 
     * <p>
     * 
     * Checks the cache first.
     * 
     * @param uuid The uuid of the player who's jail we are getting.
     * @return The jail the player is in, <strong>CAN BE NULL</strong>.
     */
    public Jail getJailPlayerIsIn(UUID uuid) {
        if(this.cache.containsKey(uuid)) {
            plugin.debug(uuid.toString() + " is in the cache (getJailPlayerIsIn).");
            return this.cache.get(uuid).getJail();
        }

        for(Jail j : jails.values())
            if(j.isPlayerJailed(uuid))
                return j;

        return null;
    }

    /**
     * Gets if the given uuid of a player is jailed or not, in all the jails and cells.
     * 
     * @param uuid The uuid of the player to check.
     * @return true if they are jailed, false if not.
     */
    public boolean isPlayerJailed(UUID uuid) {
        return getJailPlayerIsIn(uuid) != null;
    }

    /**
     * Gets the {@link Prisoner} data from for this user, if they are jailed.
     * 
     * @param uuid The uuid of prisoner who's data to get
     * @return {@link Prisoner prisoner} data.
     */
    public Prisoner getPrisoner(UUID uuid) {
        Jail j = getJailPlayerIsIn(uuid);

        return j == null ? null : j.getPrisoner(uuid);
    }

    /**
     * Gets the {@link Jail} the player is in from their last known username, null if not jailed.
     * 
     * @param username Last known username to search by
     * @return {@link Jail jail} player is in
     */
    public Jail getJailPlayerIsInByLastKnownName(String username) {
        for(Jail j : jails.values())
            for(Prisoner p : j.getAllPrisoners().values())
                if(p.getLastKnownName().equalsIgnoreCase(username))
                    return j;

        return null;
    }

    /**
     * Gets the {@link Prisoner}'s data from the last known username, returning null if no prisoner has that name.
     * 
     * @param username Last known username to go by
     * @return {@link Prisoner prisoner} data
     */
    public Prisoner getPrisonerByLastKnownName(String username) {
        for(Prisoner p : this.getAllPrisoners().values())
            if(p.getLastKnownName().equalsIgnoreCase(username))
                return p;

        return null;
    }

    /**
     * Checks if the provided username is jailed, using last known username.
     * 
     * @param username Last known username to go by
     * @return true if they are jailed, false if not
     */
    public boolean isPlayerJailedByLastKnownUsername(String username) {
        return this.getPrisonerByLastKnownName(username) != null;
    }

    /**
     * Clears a {@link Jail} of all its prisoners if the jail is provided, otherwise it releases all the prisoners in all the jails.
     * 
     * @param jail The name of the jail to release the prisoners in, null if wanting to clear all.
     * @return The resulting message to be sent to the caller of this method.
     */
    public String clearJailOfPrisoners(String jail) {
        //If they don't pass in a jail name, clear all the jails
        if(jail != null) {
            Jail j = getJail(jail);

            if(j != null) {
                for(Prisoner p : j.getAllPrisoners().values()) {
                    getPlugin().getPrisonerManager().schedulePrisonerRelease(p);
                }

                return Lang.PRISONERSCLEARED.get(j.getName());
            }else {
                return Lang.NOJAIL.get(jail);
            }
        }else {
            return clearAllJailsOfAllPrisoners();
        }
    }

    /**
     * Clears all the {@link Jail jails} of prisoners by releasing them.
     * 
     * @return The resulting message to be sent to the caller of this method.
     */
    public String clearAllJailsOfAllPrisoners() {
        //No name of a jail has been passed, so release all of the prisoners in all the jails
        if(getJails().size() == 0) {
            return Lang.NOJAILS.get();
        }else {
            for(Jail j : getJails()) {
                for(Prisoner p : j.getAllPrisoners().values()) {
                    getPlugin().getPrisonerManager().schedulePrisonerRelease(p);
                }
            }

            return Lang.PRISONERSCLEARED.get(Lang.ALLJAILS);
        }
    }

    /**
     * Forcefully clears all the jails if name provided is null.
     * 
     * <p>
     * 
     * This method just clears them from the storage, doesn't release them.
     * 
     * @param name of the jail to clear, null if all of them.
     * @return The resulting message to be sent to the caller of this method.
     */
    public String forcefullyClearJailOrJails(String name) {
        if(name == null) {
            if(getJails().size() == 0) {
                return Lang.NOJAILS.get();
            }else {
                for(Jail j : getJails()) {
                    j.clearPrisoners();
                }

                return Lang.PRISONERSCLEARED.get(Lang.ALLJAILS);
            }
        }else {
            Jail j = getJail(name);

            if(j != null) {
                j.clearPrisoners();
                return Lang.PRISONERSCLEARED.get(j.getName());
            }else {
                return Lang.NOJAIL.get(name);
            }
        }
    }

    /**
     * Deletes a jail's cell, checking everything is setup right for it to be deleted.
     * 
     * @param jail Name of the jail to delete a cell in.
     * @param cell Name of the cell to delete.
     * @return The resulting message to be sent to the caller of this method.
     */
    public String deleteJailCell(String jail, String cell) {
        //Check if the jail name provided is a valid jail
        if(isValidJail(jail)) {
            Jail j = getJail(jail);

            //check if the cell is a valid cell
            if(j.isValidCell(cell)) {
                if(j.getCell(cell).hasPrisoner()) {
                    //The cell has a prisoner, so tell them to first transfer the prisoner
                    //or release the prisoner
                    return Lang.CELLREMOVALUNSUCCESSFUL.get(new String[] { cell, jail });
                }else {
                    j.removeCell(cell);
                    return Lang.CELLREMOVED.get(new String[] { cell, jail });
                }
            }else {
                //No cell found by the provided name in the stated jail
                return Lang.NOCELL.get(new String[] { cell, jail });
            }
        }else {
            //No jail found by the provided name
            return Lang.NOJAIL.get(jail);
        }
    }

    /**
     * Deletes all the cells in a jail, returns a list of Strings.
     * 
     * @param jail The name of the jail to delete all the jails in.
     * @return An array of strings of messages to send.
     */
    public String[] deleteAllJailCells(String jail) {
        LinkedList<String> msgs = new LinkedList<String>();

        //Check if the jail name provided is a valid jail
        if(isValidJail(jail)) {
            Jail j = getJail(jail);

            if(j.getCellCount() == 0) {
                //There are no cells in this jail, thus we can't delete them.
                msgs.add(Lang.NOCELLS.get(j.getName()));
            }else {
                //Keep a local copy of the hashset so that we don't get any CMEs.
                HashSet<Cell> cells = new HashSet<Cell>(j.getCells());

                for(Cell c : cells) {
                    if(c.hasPrisoner()) {
                        //The cell has a prisoner, so tell them to first transfer the prisoner
                        //or release the prisoner
                        msgs.add(Lang.CELLREMOVALUNSUCCESSFUL.get(new String[] { c.getName(), j.getName() }));
                    }else {
                        j.removeCell(c.getName());
                        msgs.add(Lang.CELLREMOVED.get(new String[] { c.getName(), j.getName() }));
                    }
                }
            }
        }else {
            //No jail found by the provided name
            msgs.add(Lang.NOJAIL.get(jail));
        }

        return msgs.toArray(new String[msgs.size()]);
    }

    /**
     * Deletes a jail while doing some checks to verify it can be deleted.
     * 
     * @param jail The name of the jail to delete.
     * @return The resulting message to be sent to the caller of this method.
     */
    public String deleteJail(String jail) {
        //Check if the jail name provided is a valid jail
        if(isValidJail(jail)) {
            //check if the jail doesn't contain prisoners
            if(getJail(jail).getAllPrisoners().size() == 0) {
                //There are no prisoners, so we can delete it
                removeJail(jail);
                return Lang.JAILREMOVED.get(jail);
            }else {
                //The jail has prisoners, they need to release them first
                return Lang.JAILREMOVALUNSUCCESSFUL.get(jail);
            }
        }else {
            //No jail found by the provided name
            return Lang.NOJAIL.get(jail);
        }
    }

    /**
     * Returns whether or not the player is creating a jail or a cell.
     * 
     * <p>
     * 
     * If you want to check to see if they're just creating a jail then use {@link #isCreatingAJail(String) isCreatingAJail} or if you want to see if they're creating a cell then use {@link #isCreatingACell(String) isCreatingACell}.
     * 
     * @param name The name of the player, in any case as we convert it to lowercase.
     * @return True if the player is creating a jail or cell, false if they're not creating anything.
     */
    public boolean isCreatingSomething(String name) {
        return this.jailCreators.containsKey(name.toLowerCase()) || this.cellCreators.containsKey(name.toLowerCase());
    }

    /**
     * Returns a message used for telling them what they're creating and what step they're on.
     * 
     * @param player the name of the player to check
     * @return The details for the step they're on
     */
    public String getStepMessage(String player) {
        String message = "";

        if(isCreatingACell(player)) {//Check whether it is a jail cell
            CreationPlayer cp = this.getCellCreationPlayer(player);
            message = "You're already creating a Cell with the name '" + cp.getCellName() + "' and you still need to ";

            switch(cp.getStep()) {
                case 1:
                    message += "set the teleport in location.";
                    break;
                case 2:
                    message += "select all the signs.";
                    break;
                case 3:
                    message += "set the double chest location.";
                    break;
            }

        }else if(isCreatingAJail(player)) {//If not a cell, then check if a jail.
            CreationPlayer cp = this.getJailCreationPlayer(player);
            message = "You're already creating a Jail with the name '" + cp.getJailName() + "' and you still need to ";

            switch(cp.getStep()) {
                case 1:
                    message += "select the first point.";
                    break;
                case 2:
                    message += "select the second point.";
                    break;
                case 3:
                    message += "set the teleport in location.";
                    break;
                case 4:
                    message += "set the release location.";
                    break;
            }
        }

        return message;
    }

    /**
     * Returns whether or not someone is creating a <strong>Jail</strong>.
     * 
     * @param name the player's name to check
     * @return Whether they are creating a jail or not.
     */
    public boolean isCreatingAJail(String name) {
        return this.jailCreators.containsKey(name.toLowerCase());
    }

    /**
     * Method for setting a player to be creating a Jail, returns whether or not they were added successfully.
     * 
     * @param player The player who is creating a jail.
     * @param jailName The name of the jail we are creating.
     * @return True if they were added successfully, false if they are already creating a Jail.
     */
    public boolean addCreatingJail(String player, String jailName) {
        if(isCreatingAJail(player)) {
            return false;
        }else {
            this.jailCreators.put(player.toLowerCase(), new CreationPlayer(jailName));
            return true;
        }
    }

    /**
     * Returns the instance of the CreationPlayer for this player, null if there was none found.
     * 
     * @param name the player's name
     * @return gets the player's {@link CreationPlayer} instance
     */
    public CreationPlayer getJailCreationPlayer(String name) {
        return this.jailCreators.get(name.toLowerCase());
    }

    /**
     * Removes a CreationPlayer with the given name from the jail creators.
     * 
     * @param name player's name to remove
     */
    public void removeJailCreationPlayer(String name) {
        this.jailCreators.remove(name.toLowerCase());
    }

    /**
     * Returns whether or not someone is creating a <strong>Cell</strong>.
     * 
     * @param name the player's name to check
     * @return Whether they are creating a jail cell or not.
     */
    public boolean isCreatingACell(String name) {
        return this.cellCreators.containsKey(name.toLowerCase());
    }

    /**
     * Method for setting a player to be creating a Cell, returns whether or not they were added successfully.
     * 
     * @param player The player who is creating a jail.
     * @param jailName The name of the jail this cell is going.
     * @param cellName The name of the cell we are creating.
     * @return True if they were added successfully, false if they are already creating a Jail.
     */
    public boolean addCreatingCell(String player, String jailName, String cellName) {
        if(isCreatingACell(player)) {
            return false;
        }else {
            this.cellCreators.put(player.toLowerCase(), new CreationPlayer(jailName, cellName));
            return true;
        }
    }

    /**
     * Returns the instance of the CreationPlayer for this player, null if there was none found.
     * 
     * @param name the player's name to get
     * @return The player's {@link CreationPlayer} instance.
     */
    public CreationPlayer getCellCreationPlayer(String name) {
        return this.cellCreators.get(name.toLowerCase());
    }

    /**
     * Removes a CreationPlayer with the given name from the cell creators.
     * 
     * @param name player's name to remove
     */
    public void removeCellCreationPlayer(String name) {
        this.cellCreators.remove(name.toLowerCase());
    }

    /**
     * Gets the instance of the {@link JailCreationSteps}.
     * 
     * @return {@link JailCreationSteps} instance
     */
    public JailCreationSteps getJailCreationSteps() {
        return this.jcs;
    }

    /**
     * Gets the instance of the {@link CellCreationSteps}.
     * 
     * @return the {@link CellCreationSteps} instance
     */
    public CellCreationSteps getCellCreationSteps() {
        return this.ccs;
    }

    /**
     * Adds something to the confirming list.
     * 
     * @param name who to add
     * @param confirmer {@link ConfirmPlayer} of what they're confirming
     */
    public void addConfirming(String name, ConfirmPlayer confirmer) {
        getPlugin().debug("Adding a confirming for " + name + " to confirm " + confirmer.getConfirming().toString().toLowerCase());
        this.confirms.put(name, confirmer);
    }

    /**
     * Removes a name from the confirming list.
     * 
     * @param name who to remove
     */
    public void removeConfirming(String name) {
        this.confirms.remove(name);
    }

    /**
     * Checks if the given name is confirming something.
     * 
     * @param name the player's name
     * @return Whether they are confirming something or not
     */
    public boolean isConfirming(String name) {
        return this.confirms.containsKey(name);
    }

    /**
     * Returns true if the confirmation has expired, false if it is still valid.
     * 
     * @param name the player's name
     * @return Whether their confirmation has expired or not.
     */
    public boolean confirmingHasExpired(String name) {
        //If the expiry time is LESS than the current time, it has expired
        return this.confirms.get(name).getExpiryTime() < System.currentTimeMillis();
    }

    /**
     * Returns the original arguments for what we are confirming.
     * 
     * @param name the player's name
     * @return an array of strings which is their original arguments
     */
    public String[] getOriginalArgs(String name) {
        return this.confirms.get(name).getArguments();
    }

    /**
     * Returns what the given name is confirming.
     * 
     * @param name the player's name
     * @return What they are confirming
     */
    public Confirmation getWhatIsConfirming(String name) {
        return this.confirms.get(name).getConfirming();
    }
}

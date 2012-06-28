package fr.ippon.wip.config.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.ippon.wip.config.WIPConfiguration;

/**
 * This class 
 * @author ylegat
 *
 */
public class ConfigurationCacheDAO extends ConfigurationDAO {
	
	// a map that contains the loaded configurations associated with their names.
	private final Map<String, WIPConfiguration> cache;
	
	// a reference to the used DAO
	private final ConfigurationDAO dao;
	
	public ConfigurationCacheDAO(ConfigurationDAO dao) {
		this.dao = dao;
		cache = new HashMap<String, WIPConfiguration>();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public WIPConfiguration create(WIPConfiguration configuration) {
		configuration = dao.create(configuration);
		if(configuration != null)
			cache.put(configuration.getName(), (WIPConfiguration) configuration.clone());
		
		return configuration;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public WIPConfiguration read(String name) {
		WIPConfiguration configuration = cache.get(name);
		if (configuration != null) 
			return (WIPConfiguration) configuration.clone();
		
		configuration = dao.read(name);
		cache.put(name, (WIPConfiguration) configuration.clone());
		return configuration;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public WIPConfiguration update(WIPConfiguration configuration) {
		configuration = dao.update(configuration);
		if(configuration != null)
			cache.put(configuration.getName(), (WIPConfiguration) configuration.clone());
		
		return configuration;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized List<String> getConfigurationsNames() {
		return dao.getConfigurationsNames();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deploy() {
		dao.deploy();
	}

	/**
	 * {@inheritDoc}
	 */
	 @Override
	public void resetConfigurationsNames() {
		dao.resetConfigurationsNames();
	}

	@Override
	public boolean delete(String name) {
		if(!dao.delete(name))
			return false;
		
		cache.put(name, null);
		return true;
	}
}
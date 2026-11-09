package com.hazelcast.hibernate.instance;

import org.hibernate.cache.CacheException;

import java.util.Properties;

public class HazelcastMockInstanceFactory implements IHazelcastInstanceFactory
{
	private static ThreadLocal<IHazelcastInstanceLoader> loaders = new ThreadLocal<IHazelcastInstanceLoader>();

	public static void setThreadLocalLoader(IHazelcastInstanceLoader loader)
	{
		loaders.set(loader);
	}

	@Override
	public IHazelcastInstanceLoader createInstanceLoader(Properties props) throws CacheException
	{
		return loaders.get();
	}
}

package uilicious.dstackql.stack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import picoded.core.struct.GenericConvertHashMap;
import picoded.core.struct.GenericConvertMap;
import picoded.dstack.DStack;
import picoded.dstack.DataObject;
import picoded.dstack.DataObjectMap;

public class MultiStackLoader extends GenericConvertHashMap<String, DStack> {
	
	// Logger to use, for config file warnings
	// private static final Logger LOGGER = Logger.getLogger(MultiStackLoader.class.getName());
	
	// Config map
	protected GenericConvertMap<String, Object> stackConfigMap = null;
	
	/**
	 ** Initialized the multiple stacks, given the config
	 **
	 ** @param  inStackConfigMap configuration map, containing the various individual dstack config
	 */
	public MultiStackLoader(GenericConvertMap<String, Object> inStackConfigMap) {
		// set up the config
		stackConfigMap = inStackConfigMap;
		
		// Iterate each, and load each one of them
		for (String name : stackConfigMap.keySet()) {

			// Get the stack config, to validate if its "enabled"
			GenericConvertMap<String,Object> stackConfig = stackConfigMap.fetchGenericConvertStringMap(name + ".dstack");

			// Skip if not enabled
			if( stackConfig == null || stackConfig.getBoolean("enable", true) == false ) {
				continue;
			}

			// Setup the dstack object and save it
			this.put(name,new DStack(stackConfig));
		}
	}
	
	//---------------------------------------------------------------------
	//
	//  Preloader logic
	//
	//---------------------------------------------------------------------

	/**
	 ** Run the preloader, across multiple threads
	 ** 
	 ** @param threadCount maximum number of concurrent preloaders
	 */
	public void runMultiThreadedPreloader_andWait(int threadCount) {
		ExecutorService pool = runMultiThreadedPreloader( this.keySet(), threadCount );

		// Lets wait for pool for completion
		pool.shutdown();
		try {
			if( pool.awaitTermination(30L, TimeUnit.DAYS) == false ) {
				pool.shutdownNow();
				throw new RuntimeException("## [Preloader] Terminating due to 30 days timeout !");
			}
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 ** Run the preloader, across multiple threads
	 ** 
	 ** @param stackNames  stack to scan against
	 ** @param threadCount maximum number of concurrent preloaders
	 */
	public ExecutorService runMultiThreadedPreloader(Collection<String> stackNames, int threadCount) {

		// Event logging
		System.out.println("## [Preloader] Starting multi-threaded preloader with thread count : "+threadCount);

		// The executor service pool
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);

		// For each stack name, lets iterate and start submitting "preloader tasks"
		for(String stackName : stackNames) {

			// Lets fetch the individual preloader config
			GenericConvertMap<String,Object> preloaderConfig = stackConfigMap.fetchGenericConvertStringMap(stackName + ".preloader");

			// Skip various preloader stack that is not relevent
			if( preloaderConfig == null || preloaderConfig.getBoolean("enable", false) == false ) {
				continue;
			}

			// Get the DStack object
			DStack stackObj = this.get(stackName);

			// Lets load the various preloader types
			schedulePreloaderThreads(stackName, stackObj, pool, preloaderConfig, "DataObjectMap" );
		}

		// Return the pool after everything is scheduled
		return pool;
	}

	// Helper function for preloading individual threads
	protected void schedulePreloaderThreads(String stackName, DStack stack, ExecutorService pool, GenericConvertMap<String,Object> preloaderConfig, String structType ) {
		// Get the preloader config map
		GenericConvertMap<String,Object> structConfig = preloaderConfig.getGenericConvertStringMap(structType, "{}");
		List<String> structNames = new ArrayList<String>(structConfig.keySet());
		Collections.shuffle( structNames );

		// Lets iterate the struct list, in random order, and schedule them
		for(String structName : structNames) {
			// target stack to sync to (if configured)
			DStack targetStack = null;
			String targetStructName = null;

			// Get the map config (if applicable)
			GenericConvertMap<String,Object> structPreloadConfig = structConfig.fetchGenericConvertStringMap(structName, null);
			if( structPreloadConfig != null ) {
				String targetStackName = structPreloadConfig.getString("targetStack", null);
				if( targetStackName != null ) {
					targetStack = this.get(targetStackName);
					if( targetStack == null ) {
						throw new RuntimeException("Unable to perform preloader sync to targetStack (does not exists): "+targetStackName);
					}
					targetStructName = structPreloadConfig.getString("targetName", structName);
				}
			}
			
			// The runnable task to setup
			Runnable runnableTask = buildTheRunnableTask(stackName, stack, structType, structName, targetStack, targetStructName);
			if( runnableTask == null ) {
				return;
			}

			// Schedule the task
			pool.submit( runnableTask );
		}
	}

	// Build the runnable task, which is then scheduled accordingly
	protected static Runnable buildTheRunnableTask(String stackName, DStack stack, String structType, String structName, DStack targetStack, String targetStructName) {
		if( structType.equalsIgnoreCase("DataObjectMap") ) {
			return () -> {
				try {
					// Log the output
					System.out.println("[Preloader-"+structType+"] "+stackName+"."+structName+" : Starting Preloading ...");
					long count = 0;

					// Get the DataObjectMap
					DataObjectMap dom = stack.getDataObjectMap(structName);

					// Lets iterate it 
					DataObject obj = dom.looselyIterateObject(null);
					while( obj != null ) {
						try {
							if(targetStack != null) {
								// Lets sync it to a target
								DataObjectMap targetDom = targetStack.getDataObjectMap(targetStructName);

								// Get the unchecked data object, to sync data to
								DataObject targetObject = targetDom.get(obj._oid(), true);
								targetObject.putAll(obj);

								// And sync it
								targetObject.saveDelta();
							} else {
								// Lets load the keyset, to help ensure data is loaded
								// if target syncing is not required
								obj.keySet();
							}
							
							// lets go to the next object
							obj = dom.looselyIterateObject(obj);

							// Increment and log it
							count++;
							if( count % 100 == 1 ) {
								System.out.println("[Preloader-"+structType+"] "+stackName+"."+structName+" : Preloading ("+count+"/"+dom.size()+") "+obj._oid());
							}

						} catch (Exception e) {
							e.printStackTrace();
						}
					}

					// Finish the output
					System.out.println("[Preloader-"+structType+"] "+stackName+"."+structName+" : Finished Preloading ("+dom.size()+") !!!");
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
		}
		return null;
	}

}
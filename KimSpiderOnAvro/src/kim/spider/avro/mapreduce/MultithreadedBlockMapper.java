/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kim.spider.avro.mapreduce;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.MapContext;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.StatusReporter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.map.WrappedMapper;
import org.apache.hadoop.mapreduce.task.MapContextImpl;
import org.apache.hadoop.util.ReflectionUtils;

@InterfaceAudience.Public
@InterfaceStability.Stable
public class MultithreadedBlockMapper<K1, V1, K2, V2> extends Mapper<K1, V1, K2, V2> {

	private static final Log															LOG					= LogFactory
																																				.getLog(MultithreadedBlockMapper.class);
	public static String																	NUM_THREADS	= "mapreduce.mapper.multithreadedmapper.threads";
	public static String																	MAP_CLASS		= "mapreduce.mapper.multithreadedmapper.mapclass";
	public static String																	MAP_TASK_TIMEOUT		= "mapred.task.timeout";

	private Class<? extends BlockMapper<K1, V1, K2, V2>>	mapClass;
	private Context																				outer;
	private List<MapRunner>																runners;

	/**
	 * The number of threads in the thread pool that will run the map function.
	 * 
	 * @param job
	 *          the job
	 * @return the number of threads
	 */
	public static int getNumberOfThreads(JobContext job) {
		return job.getConfiguration().getInt(NUM_THREADS, 10);
	}
	
	/**
	 * The time of threads timeout.
	 * 
	 * @param job
	 *          the job
	 * @return the time out
	 */
	public static void setThreadTimeOut(JobContext job,int timeout) {
		job.getConfiguration().setInt(MAP_TASK_TIMEOUT, timeout);
	}

	/**
	 * Set the number of threads in the pool for running maps.
	 * 
	 * @param job
	 *          the job to modify
	 * @param threads
	 *          the new number of threads
	 */
	public static void setNumberOfThreads(Job job, int threads) {
		job.getConfiguration().setInt(NUM_THREADS, threads);
	}

	/**
	 * Get the application's mapper class.
	 * 
	 * @param <K1>
	 *          the map's input key type
	 * @param <V1>
	 *          the map's input value type
	 * @param <K2>
	 *          the map's output key type
	 * @param <V2>
	 *          the map's output value type
	 * @param job
	 *          the job
	 * @return the mapper class to run
	 */
	@SuppressWarnings("unchecked")
	public static <K1, V1, K2, V2> Class<BlockMapper<K1, V1, K2, V2>> getMapperClass(
			JobContext job) {
		return (Class<BlockMapper<K1, V1, K2, V2>>) job.getConfiguration()
				.getClass(MAP_CLASS, BlockMapper.class);
	}

	/**
	 * Set the application's mapper class.
	 * 
	 * @param <K1>
	 *          the map input key type
	 * @param <V1>
	 *          the map input value type
	 * @param <K2>
	 *          the map output key type
	 * @param <V2>
	 *          the map output value type
	 * @param job
	 *          the job to modify
	 * @param cls
	 *          the class to use as the mapper
	 */
	public static <K1, V1, K2, V2> void setMapperClass(Job job,
			Class<? extends BlockMapper<K1, V1, K2, V2>> cls) {
		if (MultithreadedBlockMapper.class.isAssignableFrom(cls)) {
			throw new IllegalArgumentException("Can't have recursive "
					+ "MultithreadedMapper instances.");
		}
		job.getConfiguration().setClass(MAP_CLASS, cls, Mapper.class);
	}

	/**
	 * Run the application's maps using a thread pool.
	 */
	@Override
	public void run(Context context) throws IOException, InterruptedException {
		outer = context;
		int numberOfThreads = getNumberOfThreads(context);
		mapClass = getMapperClass(context);
		if (LOG.isDebugEnabled()) {
			LOG.debug("Configuring multithread runner to use " + numberOfThreads
					+ " threads");
		}

		runners = new ArrayList<MapRunner>(numberOfThreads);
		for (int i = 0; i < numberOfThreads; ++i) {
			MapRunner thread = new MapRunner(context);
			thread.start();
			runners.add(i, thread);
		}

		// select a timeout that avoids a task timeout
		long timeout = context.getConfiguration().getInt("mapred.task.timeout",
				5 * 60 * 1000);
	
		int numblockthread = 0;
		while (getActiveThread() > 0) {
			for (int i = 0; i < numberOfThreads; i++) {
				MapRunner thread = runners.get(i);
				if (thread.isAlive()) {
					if (thread.mapper.getBlockTime() > timeout) {
						thread.interrupt();
						thread.mapper.BlockRecord();
						numblockthread ++;
						break;
					}
				}
			}
		}
		LOG.warn("Aborting with " + numblockthread
				+ " block threads.");
		
	}

	public int getActiveThread() {
		int iret = 0;
		for (MapRunner thread : runners) {
			if (thread.isAlive())
				iret++;
		}
		return iret;
	}

	private class SubMapRecordReader extends RecordReader<K1, V1> {
		private K1						key;
		private V1						value;
		private Configuration	conf;

		@Override
		public void close() throws IOException {
		}

		@Override
		public float getProgress() throws IOException, InterruptedException {
			return 0;
		}

		@Override
		public void initialize(InputSplit split, TaskAttemptContext context)
				throws IOException, InterruptedException {
			conf = context.getConfiguration();
		}

		@Override
		public boolean nextKeyValue() throws IOException, InterruptedException {
			synchronized (outer) {
				if (!outer.nextKeyValue()) {
					return false;
				}
				key = ReflectionUtils.copy(outer.getConfiguration(),
						outer.getCurrentKey(), key);
				value = ReflectionUtils.copy(conf, outer.getCurrentValue(), value);
				return true;
			}
		}

		public K1 getCurrentKey() {
			return key;
		}

		@Override
		public V1 getCurrentValue() {
			return value;
		}
	}

	private class SubMapRecordWriter extends RecordWriter<K2, V2> {

		@Override
		public void close(TaskAttemptContext context) throws IOException,
				InterruptedException {
		}

		@Override
		public void write(K2 key, V2 value) throws IOException,
				InterruptedException {
			synchronized (outer) {
				outer.write(key, value);
			}
		}
	}

	private class SubMapStatusReporter extends StatusReporter {

		@Override
		public Counter getCounter(Enum<?> name) {
			return outer.getCounter(name);
		}

		@Override
		public Counter getCounter(String group, String name) {
			return outer.getCounter(group, name);
		}

		@Override
		public void progress() {
			outer.progress();
		}

		@Override
		public void setStatus(String status) {
			outer.setStatus(status);
		}

	}

	private class MapRunner extends Thread {
		public BlockMapper<K1, V1, K2, V2>	mapper;
		private Context											subcontext;
		private Throwable										throwable;

		MapRunner(Context context) throws IOException, InterruptedException {
			mapper = ReflectionUtils
					.newInstance(mapClass, context.getConfiguration());
			MapContext<K1, V1, K2, V2> mapContext = new MapContextImpl<K1, V1, K2, V2>(
					outer.getConfiguration(), outer.getTaskAttemptID(),
					new SubMapRecordReader(), new SubMapRecordWriter(),
					context.getOutputCommitter(), new SubMapStatusReporter(),
					outer.getInputSplit());
			subcontext = new WrappedMapper<K1, V1, K2, V2>()
					.getMapContext(mapContext);

		}

		public Throwable getThrowable() {
			return throwable;
		}

		@Override
		public void run() {
			try {
				mapper.run(subcontext);
			} catch (Throwable ie) {
				throwable = ie;
			}
		}

	}

	public static abstract class BlockMapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT>
			extends Mapper<KEYIN, VALUEIN, KEYOUT, VALUEOUT> {
		
		private long		lasttime = System.currentTimeMillis();
		public KEYIN		currentKey;
		public VALUEIN	currentValue;
		@Override
		public void run(Context context) throws IOException, InterruptedException {
			setup(context);
			while (context.nextKeyValue()) {
				lasttime = System.currentTimeMillis();
				currentKey = context.getCurrentKey();
				currentValue = context.getCurrentValue();
				map(currentKey, currentValue, context);
			}
			cleanup(context);
		}

		public long getBlockTime() {
			return System.currentTimeMillis() - lasttime;
		}
		
		public abstract void BlockRecord()  throws InterruptedException ;

		
	}

}

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

import kim.spider.io.AvroKeyComparator;
import kim.spider.serializer.avro.AvroReflectSerialization;
import kim.spider.serializer.avro.AvroSpecificSerialization;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.serializer.WritableSerialization;
import org.apache.hadoop.mapreduce.Job;

/** An {@link org.apache.hadoop.mapred.InputFormat} for sequence files. */
public class AvroJob {

	public static Job getAvroJob(Configuration conf) throws IOException 
	{
		 Job job = new Job(conf);
		 job.setJarByClass(AvroJob.class);
		 job.setSortComparatorClass(AvroKeyComparator.class);
		 job.getConfiguration().setStrings("io.serializations", 
		      new String[]{WritableSerialization.class.getName(), 
		        //AvroSpecificSerialization.class.getName(), 
		        AvroReflectSerialization.class.getName()});
		 return job;
	}
}

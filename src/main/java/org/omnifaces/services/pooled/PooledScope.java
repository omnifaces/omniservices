/*
 * Copyright 2020 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.services.pooled;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.spi.Contextual;

class PooledScope {

	private final Map<Contextual<?>, PoolKey<?>> allocatedPoolKeys = new HashMap<>();

	public void setPoolKey(PoolKey<?> pookKey) {
		allocatedPoolKeys.put(pookKey.contextual(), pookKey);
	}

	public void removePoolKey(PoolKey<?> poolKey) {
		allocatedPoolKeys.remove(poolKey.contextual());
	}

	@SuppressWarnings("unchecked")
	public <T> PoolKey<T> getPoolKey(Contextual<T> contextual) {
		return (PoolKey<T>) allocatedPoolKeys.get(contextual);
	}
}

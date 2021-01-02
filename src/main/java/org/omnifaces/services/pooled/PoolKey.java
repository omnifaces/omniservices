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

import java.util.Objects;

import javax.enterprise.context.spi.Contextual;

final class PoolKey<T> {

	private final Contextual<T> contextual;
	private final int index;

	public PoolKey(Contextual<T> contextual, int index) {
		this.contextual = contextual;
		this.index = index;
	}

	Contextual<T> contextual() {
		return contextual;
	}

	int index() {
		return index;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		PoolKey poolKey = (PoolKey) o;
		return index == poolKey.index &&
				Objects.equals(contextual, poolKey.contextual);
	}

	@Override
	public int hashCode() {
		return Objects.hash(contextual, index);
	}
}

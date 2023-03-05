import org.omnifaces.services.CdiExtension;

import jakarta.enterprise.inject.spi.Extension;

/*
 * Copyright 2021, 2023 OmniFaces
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
/**
 * @author Arjan Tijms
 */
module org.omnifaces.services {

    provides Extension with CdiExtension;

    exports org.omnifaces.services;
    opens org.omnifaces.services;

    exports org.omnifaces.services.asynchronous;
    opens org.omnifaces.services.asynchronous;

    exports org.omnifaces.services.pooled;
    opens org.omnifaces.services.pooled;

    exports org.omnifaces.services.lock;
    opens org.omnifaces.services.lock;

    exports org.omnifaces.services.util;
    opens org.omnifaces.services.util;

    requires transitive java.transaction.xa;

    requires java.naming;
    requires jakarta.cdi;
    requires jakarta.ejb;
    requires org.omnifaces.utils;
}

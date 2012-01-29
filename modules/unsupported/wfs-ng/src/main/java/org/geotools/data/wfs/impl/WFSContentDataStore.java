package org.geotools.data.wfs.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import org.geotools.data.store.ContentDataStore;
import org.geotools.data.store.ContentEntry;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.feature.NameImpl;
import org.opengis.feature.type.Name;

class WFSContentDataStore extends ContentDataStore {

    private final WFSClient client;

    private final Map<Name, QName> names;

    public WFSContentDataStore(final WFSClient client) {
        this.client = client;
        this.names = new ConcurrentHashMap<Name, QName>();
    }

    public QName getRemoteTypeName(Name localTypeName) {
        QName qName = names.get(localTypeName);
        if (null == qName) {
            throw new NoSuchElementException(localTypeName.toString());
        }
        return qName;
    }

    /**
     * @see org.geotools.data.store.ContentDataStore#createTypeNames()
     */
    @Override
    protected List<Name> createTypeNames() throws IOException {
        Set<QName> remoteTypeNames = client.getRemoteTypeNames();
        List<Name> names = new ArrayList<Name>(remoteTypeNames.size());
        for (QName remoteTypeName : remoteTypeNames) {
            String localTypeName = remoteTypeName.getLocalPart();
            if (!XMLConstants.DEFAULT_NS_PREFIX.equals(remoteTypeName.getPrefix())) {
                localTypeName = remoteTypeName.getPrefix() + "_" + localTypeName;
            }
            Name typeName = new NameImpl(getNamespaceURI(), localTypeName);
            names.add(typeName);
            this.names.put(typeName, remoteTypeName);
        }
        return names;
    }

    /**
     * @see org.geotools.data.store.ContentDataStore#createFeatureSource(org.geotools.data.store.ContentEntry)
     */
    @Override
    protected ContentFeatureSource createFeatureSource(final ContentEntry entry) throws IOException {
        ContentFeatureSource source;

        source = new WFSContentFeatureSource(entry, client);

        if (client.supportsTransaction(entry.getTypeName())) {
            source = new WFSContentFeatureStore((WFSContentFeatureSource) source);
        }

        return source;
    }

    /**
     * @see WFSDataStore#getInfo()
     */
    @Override
    public WFSServiceInfo getInfo() {
        return client.getInfo();
    }

}

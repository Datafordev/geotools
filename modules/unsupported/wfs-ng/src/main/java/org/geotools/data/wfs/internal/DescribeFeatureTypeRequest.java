package org.geotools.data.wfs.internal;

import static org.geotools.data.wfs.internal.WFSOperationType.DESCRIBE_FEATURETYPE;

import javax.xml.namespace.QName;

public class DescribeFeatureTypeRequest extends WFSRequest {

    private QName typeName;

    public DescribeFeatureTypeRequest(WFSConfig config, WFSStrategy strategy) {
        super(DESCRIBE_FEATURETYPE, config, strategy);
    }

    public void setTypeName(QName typeName) {
        this.typeName = typeName;
    }

    public QName getTypeName() {
        return typeName;
    }
}

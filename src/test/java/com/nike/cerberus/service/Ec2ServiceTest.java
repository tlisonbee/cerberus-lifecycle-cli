/*
 * Copyright (c) 2016 Nike, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nike.cerberus.service;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.AvailabilityZoneState;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeKeyPairsRequest;
import com.amazonaws.services.ec2.model.DescribeKeyPairsResult;
import com.amazonaws.services.ec2.model.ImportKeyPairRequest;
import com.amazonaws.services.ec2.model.ImportKeyPairResult;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Ec2ServiceTest {

    @Test
    public void testImportKey() {

        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String keyName = "key-name";
        String publicKeyMaterial = "public-key-material";
        String keyNameResult = "key-name-result";

        when(ec2Client.importKeyPair(new ImportKeyPairRequest(keyName, publicKeyMaterial)))
                .thenReturn(new ImportKeyPairResult().withKeyName(keyNameResult));

        // invoke method under test
        String result = ec2Service.importKey(keyName, publicKeyMaterial);

        assertEquals(keyNameResult, result);
    }

    @Test
    public void testIsKeyPairPresentTrue() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String keyName = "key-name";

        when(ec2Client.describeKeyPairs(
                new DescribeKeyPairsRequest()
                        .withKeyNames(keyName)
                )
        ).thenReturn(
                new DescribeKeyPairsResult()
                        .withKeyPairs(
                                new KeyPairInfo()
                        )
        );

        // invoke method under test
        assertTrue(ec2Service.isKeyPairPresent(keyName));
    }

    @Test
    public void testIsKeyPairPresentFalse() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String keyName = "key-name";

        when(ec2Client.describeKeyPairs(new DescribeKeyPairsRequest().withKeyNames(keyName)))
                .thenReturn(new DescribeKeyPairsResult());

        // invoke method under test
        assertFalse(ec2Service.isKeyPairPresent(keyName));
    }

    @Test
    public void testIsKeyPairPresentFalseNotFound() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String keyName = "key-name";

        AmazonServiceException ex = new AmazonServiceException("fake-exception");
        ex.setErrorCode("InvalidKeyPair.NotFound");

        when(ec2Client.describeKeyPairs(new DescribeKeyPairsRequest().withKeyNames(keyName)))
                .thenThrow(ex);

        // invoke method under test
        assertFalse(ec2Service.isKeyPairPresent(keyName));
    }

    @Test
    public void testIsKeyPairPresentException() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String keyName = "key-name";
        String fakeExceptionMessage = "fake-exception";

        when(ec2Client.describeKeyPairs(new DescribeKeyPairsRequest().withKeyNames(keyName)))
                .thenThrow(new AmazonServiceException(fakeExceptionMessage));

        try {
            // invoke method under test
            ec2Service.isKeyPairPresent(keyName);
            fail("expected exception not passed up");
        } catch (AmazonServiceException ex) {
            // pass
            assertEquals(fakeExceptionMessage, ex.getErrorMessage());
        }
    }

    @Test
    public void testGetAvailabilityZones() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String zoneName = "zone-name";

        when(ec2Client.describeAvailabilityZones()).thenReturn(
                new DescribeAvailabilityZonesResult()
                        .withAvailabilityZones(
                                new AvailabilityZone()
                                        .withZoneName(zoneName)
                                        .withState(AvailabilityZoneState.Available),
                                new AvailabilityZone()
                                        .withZoneName("not-available-zone")
                                        .withState(AvailabilityZoneState.Unavailable)
                        )
        );

        // invoke method under test
        List<String> results = ec2Service.getAvailabilityZones();

        assertEquals(1, results.size());
        assertEquals(zoneName, results.get(0));
    }

    @Test
    public void isAmiWithTagExistTrue() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String amiId = "ami-1234abcd";
        String tagName = "sometag";
        String tagValue = "someval";

        when(ec2Client.describeImages(
                new DescribeImagesRequest()
                        .withFilters(new Filter().withName(tagName).withValues(tagValue))
                        .withFilters(new Filter().withName("image-id").withValues(amiId))
                )
        ).thenReturn(
                new DescribeImagesResult().withImages(new Image())
                );

        // invoke method under test
        assertTrue(ec2Service.isAmiWithTagExist(amiId, tagName, tagValue));
    }

    @Test
    public void isAmiWithTagExistFalse() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String amiId = "ami-1234abcd";
        String tagName = "sometag";
        String tagValue = "someval";

        when(ec2Client.describeImages(
                new DescribeImagesRequest()
                        .withFilters(new Filter().withName(tagName).withValues(tagValue))
                        .withFilters(new Filter().withName("image-id").withValues(amiId))
                )
        ).thenReturn(
                new DescribeImagesResult()
                );

        // invoke method under test
        assertFalse(ec2Service.isAmiWithTagExist(amiId, tagName, tagValue));
    }

    @Test
    public void isAmiWithTagExistNotFound() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String amiId = "ami-1234abcd";
        String tagName = "sometag";
        String tagValue = "someval";

        AmazonServiceException ex = new AmazonServiceException("fake-exception");
        ex.setErrorCode("InvalidAMIID.NotFound");

        when(ec2Client.describeImages(
                new DescribeImagesRequest()
                        .withFilters(new Filter().withName(tagName).withValues(tagValue))
                        .withFilters(new Filter().withName("image-id").withValues(amiId))
                )
        ).thenThrow(ex);

        // invoke method under test
        assertFalse(ec2Service.isAmiWithTagExist(amiId, tagName, tagValue));
    }

    @Test
    public void isAmiWithTagExistThrowException() {
        AmazonEC2 ec2Client = mock(AmazonEC2.class);
        Ec2Service ec2Service = new Ec2Service(ec2Client);

        String amiId = "ami-1234abcd";
        String tagName = "sometag";
        String tagValue = "someval";
        String unknownAwsExMessage = "Unknown AWS exception message";

        when(ec2Client.describeImages(
                new DescribeImagesRequest()
                        .withFilters(new Filter().withName(tagName).withValues(tagValue))
                        .withFilters(new Filter().withName("image-id").withValues(amiId))
                )
        ).thenThrow(new AmazonServiceException(unknownAwsExMessage));

        try {
            // invoke method under test
            ec2Service.isAmiWithTagExist(amiId, tagName, tagValue);
            fail("Expected exception message '" + unknownAwsExMessage + "'not received");
        } catch (AmazonServiceException ex) {
            // pass
            assertEquals(unknownAwsExMessage, ex.getErrorMessage());
        }
    }
}
// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package com.amazon.ion.impl;

import com.amazon.ion.IonCursor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.util.function.Consumer;

import static com.amazon.ion.BitUtils.bytes;
import static com.amazon.ion.IonCursor.Event.VALUE_READY;
import static com.amazon.ion.IonCursor.Event.START_CONTAINER;
import static com.amazon.ion.IonCursor.Event.START_SCALAR;
import static com.amazon.ion.impl.IonCursorTestUtilities.STANDARD_BUFFER_CONFIGURATION;
import static com.amazon.ion.impl.IonCursorTestUtilities.Expectation;
import static com.amazon.ion.impl.IonCursorTestUtilities.ExpectationProvider;
import static com.amazon.ion.impl.IonCursorTestUtilities.STEP_IN;
import static com.amazon.ion.impl.IonCursorTestUtilities.STEP_OUT;
import static com.amazon.ion.impl.IonCursorTestUtilities.assertSequence;
import static com.amazon.ion.impl.IonCursorTestUtilities.container;
import static com.amazon.ion.impl.IonCursorTestUtilities.endContainer;
import static com.amazon.ion.impl.IonCursorTestUtilities.endStream;
import static com.amazon.ion.impl.IonCursorTestUtilities.scalar;
import static com.amazon.ion.impl.IonCursorTestUtilities.startContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IonCursorBinaryTest {

    private static IonCursorBinary initializeCursor(boolean constructFromBytes, int... data) {
        IonCursorBinary cursor;
        if (constructFromBytes) {
            cursor = new IonCursorBinary(STANDARD_BUFFER_CONFIGURATION, bytes(data), 0, data.length);
        } else {
            cursor = new IonCursorBinary(
                STANDARD_BUFFER_CONFIGURATION,
                new ByteArrayInputStream(bytes(data)),
                null,
                0,
                0
            );
        }
        cursor.registerOversizedValueHandler(STANDARD_BUFFER_CONFIGURATION.getOversizedValueHandler());
        return cursor;
    }

    /**
     * Provides Expectations that verify that advancing the cursor to the next value results in the given event, and
     * filling that value results in a Marker with the given start and end indices.
     */
    private static ExpectationProvider<IonCursorBinary> fill(IonCursor.Event expectedEvent, int expectedStart, int expectedEnd) {
        return consumer -> consumer.accept(new Expectation<>(
            String.format("fill(%s, %d, %d)", expectedEvent, expectedStart, expectedEnd),
            cursor -> {
                assertEquals(expectedEvent, cursor.nextValue());
                assertEquals(VALUE_READY, cursor.fillValue());
                Marker marker = cursor.getValueMarker();
                assertEquals(expectedStart, marker.startIndex);
                assertEquals(expectedEnd, marker.endIndex);
            }
        ));
    }

    /**
     * Provides Expectations that verify that advancing the cursor positions it on a scalar, and filling that scalar
     * results in a Marker with the given start and end indices.
     */
    private static ExpectationProvider<IonCursorBinary> fillScalar(int expectedStart, int expectedEnd) {
        return fill(START_SCALAR, expectedStart, expectedEnd);
    }

    /**
     * Provides Expectations that verify 1) that advancing the cursor positions it on a container, 2) filling that
     * container results in a Marker with the given start and end indices, and 3) the container's child values match
     * the given expectations.
     */
    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static ExpectationProvider<IonCursorBinary> fillContainer(int expectedStart, int expectedEnd, ExpectationProvider<IonCursorBinary>... expectations) {
        return consumer -> {
            fill(START_CONTAINER, expectedStart, expectedEnd).accept(consumer);
            consumer.accept((Expectation<IonCursorBinary>) STEP_IN);
            for (Consumer<Consumer<Expectation<IonCursorBinary>>> expectation : expectations) {
                expectation.accept(consumer);
            }
            consumer.accept((Expectation<IonCursorBinary>) STEP_OUT);
        };
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void basicContainer(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD3, // Struct length 3
            0x84, // Field SID 4
            0x21, 0x01 // Int length 1, starting at byte index 7
        );
        assertSequence(
            cursor,
            container(
                fillScalar(7, 8)
            ),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void basicStrings(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0x83, 'f', 'o', 'o', // String length 3, starting at byte index 5
            0x83, 'b', 'a', 'r' // String length 3, starting at byte index 9
        );
        assertSequence(
            cursor,
            fillScalar(5, 8),
            fillScalar(9, 12),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void basicNoFill(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD3, // Struct length 3
            0x84, // Field SID 4
            0x21, 0x01 // Int length 1, starting at byte index 7
        );
        assertSequence(
            cursor,
            container(
                scalar(),
                endContainer()
            ),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void basicStepOutEarly(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD3, // Struct length 3
            0x84, // Field SID 4
            0x21, 0x01 // Int length 1, starting at byte index 7
        );
        assertSequence(
            cursor,
            container(
                scalar()
            ),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void basicTopLevelSkip(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD3, // Struct length 3
            0x84, // Field SID 4
            0x21, 0x01 // Int length 1, starting at byte index 7
        );
        assertSequence(
            cursor,
            startContainer(),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void basicTopLevelSkipThenConsume(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD3, // Struct length 3
            0x84, // Field SID 4
            0x21, 0x01, // Int length 1, starting at byte index 7
            0x21, 0x03 // Int length 1, starting at byte index 9
        );
        assertSequence(
            cursor,
            startContainer(),
            fillScalar(9, 10),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void nestedContainers(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD6, // Struct length 6
            0x83, // Field SID 3
            0xB1, // List length 1
            0x40, // Float length 0
            0x84, // Field SID 4
            0x21, 0x01 // Int length 1, starting at byte index 10
        );
        assertSequence(
            cursor,
            container(
                container(
                    scalar()
                ),
                fillScalar(10, 11)
            ),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void fillContainerAtDepth0(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD6, // Struct length 6, contents start at index 5
            0x83, // Field SID 3
            0xB1, // List length 1
            0x40, // Float length 0
            0x84, // Field SID 4
            0x21, 0x01 // Int length 1, starting at byte index 10
        );
        assertSequence(
            cursor,
            fillContainer(5, 11,
                container(
                    scalar()
                ),
                fillScalar(10, 11)
            ),
            endStream()
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void fillContainerAtDepth1(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD6, // Struct length 6
            0x83, // Field SID 3
            0xB1, // List length 1, contents start at index 7
            0x40, // Float length 0
            0x84, // Field SID 4
            0x21, 0x01 // Int length 1, starting at byte index 10
        );
        assertSequence(
            cursor,
            container(
                fillContainer(7, 8,
                    scalar(),
                    endContainer()
                )
            )
        );
    }

    @ParameterizedTest(name = "constructFromBytes={0}")
    @ValueSource(booleans = {true, false})
    public void fillContainerThenSkip(boolean constructFromBytes) {
        IonCursorBinary cursor = initializeCursor(
            constructFromBytes,
            0xE0, 0x01, 0x00, 0xEA,
            0xD6, // Struct length 6
            0x83, // Field SID 3
            0xB1, // List length 1, contents start at index 7
            0x40, // Float length 0
            0x84, // Field SID 4
            0x21, 0x01, // Int length 1, starting at byte index 10
            0xD2, // Struct length 2
            0x84, // Field SID 4
            0x20 // Int length 0, at byte index 14
        );
        assertSequence(
            cursor,
            fill(START_CONTAINER, 5, 11),
            container(
                fillScalar(14, 14)
            ),
            endStream()
        );
    }
}

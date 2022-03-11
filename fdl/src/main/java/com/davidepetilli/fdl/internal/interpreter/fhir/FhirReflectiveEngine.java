package com.davidepetilli.fdl.internal.interpreter.fhir;

import ca.uhn.fhir.model.api.IElement;
import com.davidepetilli.fdl.internal.error.RuntimeError;
import com.davidepetilli.fdl.internal.lexer.Token;
import com.davidepetilli.fdl.internal.interpreter.TypeService;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.time.LocalDate;
import java.util.*;

/**
 * The {@code FhirReflectiveEngine} exposes methods to manipulate FHIR elements through the HAPIFhir library.
 * The Resources, Types, attribute etc. are known at runtime, for this reason this class makes use of Java reflection
 * to manipulate them.
 * <p>
 * The methods on this class throw {@link RuntimeError} exceptions when elements or resources cannot be found. This
 * allows to report FDL runtime errors.
 * <p>
 * Since the methods on this class can be a little tricky to follow, some of them contain small description of the
 * implementation details.
 *
 * @author Davide Petilli
 * @since 0.1
 */
public class FhirReflectiveEngine {
    private static final String FHIR_R4_MODEL_PACKAGE = "org.hl7.fhir.r4.model";

    private static final String INVALID_FHIR_RESOURCE_ERROR = "'%s' is not a valid FHIR resource.";
    private static final String INDEX_OUT_OF_ORDER_ERROR = "index out of order (must start at '0' and must not skip positions).";
    private static final String WRONG_PROPERTY_TYPE_ERROR = "the property does not expect a %s value.";
    private static final String FIELD_OR_PROPERTY_INVALID_ERROR = "the field doesn't exist or the resource type doesn't match.";

    private final TypeService typeService;

    public FhirReflectiveEngine(TypeService typeService) {
        this.typeService = typeService;
    }

    /**
     * Instantiates a FHIR Element, for instance a Resource or a Type.
     *
     * @param elementToken the {@link Token} representing the Element
     * @return the instantiated Element
     * @throws RuntimeError if the constructor can't be found or if it can't be instantiated
     */
    Object instantiateElement(Token elementToken) {
        try {
            String resourceClassName = FHIR_R4_MODEL_PACKAGE + "." + elementToken.lexeme();
            var constructors = Class.forName(resourceClassName).getDeclaredConstructors();
            var optionalConstructor = Arrays.stream(constructors)
                    .filter(c -> c.getParameterCount() == 0)
                    .findAny();
            if (optionalConstructor.isPresent()) {
                return optionalConstructor.get().newInstance();
            } else {
                var message = String.format(INVALID_FHIR_RESOURCE_ERROR, elementToken.lexeme());
                throw new RuntimeError(elementToken, message);
            }
        } catch (ReflectiveOperationException e) {
            var message = String.format(INVALID_FHIR_RESOURCE_ERROR, elementToken.lexeme());
            throw new RuntimeError(elementToken, message);
        }
    }

    /**
     * This is acts as a getter for a property on a FHIR Element. If the property is a {@link BackboneElement}, it
     * initializes it if it is the first access.
     *
     * @param propertyToken the {@link Token} representing the property name
     * @param element       the FHIR Element to interrogate
     * @param index         the index of the element if it is a {@link List}
     * @return the value retrieved from the property
     * @throws RuntimeError if it can't find a setter method or if the invocation of the method fails
     */
    Object getPropertyFromElement(Token propertyToken, Object element, Integer index) {
        var elementClass = element.getClass();
        var fieldName = propertyToken.lexeme();

        Method getterMethod = getterMethod(elementClass, fieldName).orElseThrow(() ->
                new RuntimeError(propertyToken, FIELD_OR_PROPERTY_INVALID_ERROR));

        // Here we handle simplified notation: we first check if the method returns a List of Element.
        // If it is an Element, we get the actual elements List and check if it is empty. If it is, we create
        // a new element of the concrete type, otherwise we get the element already present at 'index' position.
        var actualReturnType = getActualReturnType(getterMethod);
        if (Element.class.isAssignableFrom(actualReturnType)) {
            try {
                var returnType = getterMethod.getReturnType();
                if (List.class.isAssignableFrom(returnType)) {
                    var list = (List<?>) getterMethod.invoke(element);
                    if (list.isEmpty() || list.size() - 1 < index) {
                        var constructors = actualReturnType.getDeclaredConstructors();
                        var optionalConstructor = Arrays.stream(constructors)
                                .filter(c -> c.getParameterCount() == 0)
                                .findAny();
                        if (optionalConstructor.isPresent()) {
                            var constructor = optionalConstructor.get();
                            var newInstance = constructor.newInstance();
                            String adderName = makeMethodName(fieldName, "add");
                            Method adderMethod = elementClass.getMethod(adderName, newInstance.getClass());
                            Method indexOfMethod = list.getClass().getMethod("indexOf", Object.class);

                            adderMethod.invoke(element, newInstance);

                            if (!index.equals(indexOfMethod.invoke(list, newInstance))) {
                                throw new RuntimeError(propertyToken, INDEX_OUT_OF_ORDER_ERROR);
                            }
                            return newInstance;
                        }
                    } else {
                        return list.get(index);
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeError(propertyToken, FIELD_OR_PROPERTY_INVALID_ERROR);
            }
        }

        // It is not a BackboneElement, so we perform a simple get.
        try {
            return getterMethod.invoke(element);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeError(propertyToken, FIELD_OR_PROPERTY_INVALID_ERROR);
        }
    }

    /**
     * This method sets a property on a FHIR element.
     * <p>
     * The method operates by trials: first it checks if it can add the value directly on the Element. If no success,
     * then it tries to interpret it as a 'required' property.
     * In FHIR, 'required' properties accept values comprised in an enumeration (note that 'required' does not mean
     * mandatory, in this case).
     * Again, if no luck, then it tries to interpret the property as a Java {@link Collection}.
     * If all the above fail, it tries to set the property using a plain setter {@code setFieldName}.
     *
     * @param element the element to which to set the property
     * @param value   the value to set
     * @param field   the {@link Token} of the field
     * @param index   the index of the element in the list
     * @return the {@link Optional<Object>} containing the element with the property set
     */
    Optional<Object> trySetPropertyOnElement(Object element, Object value, Token field, Integer index) {
        var elementClass = element.getClass();
        var accessorOptional = getOptionalAccessor(field.lexeme(), elementClass);

        if (accessorOptional.isPresent()) {
            var accessor = accessorOptional.get();
            // First let's see if it is an addable primitive, if it is, we assume te object is in charge
            // of managing the underlying collection.
            var result = tryAdd(element, value, field, accessor);
            if (result.isPresent()) return result;
            // Let's see if the value is 'required', which means it is an Enum in Java terms.
            result = trySetRequiredValue(element, value, field, accessor);
            if (result.isPresent()) return result;
            // Let's try if the field is a Collection not hidden by the Element class.
            result = tryAddToCollection(element, value, field, index, accessor);
            if (result.isPresent()) return result;
            // Let's see if it is a date.
            result = trySetDate(element, value, field, accessor);
            if (result.isPresent()) return result;
            // Let's see if we can set the value directly.
            result = trySet(element, value, field);
            if (result.isPresent()) return result;
            // Let's see if it is a number
            result = trySetInteger(element, value, field);
            if (result.isPresent()) return result;
            result = trySetDecimal(element, value, field);
            if (result.isPresent()) return result;

            throw new RuntimeError(field, FIELD_OR_PROPERTY_INVALID_ERROR);
        }

        return Optional.empty();
    }

    /**
     * Sets a property on an Element, without specifying the {@code index}.
     *
     * @param element the element to which to set the property
     * @param value   the value to set
     * @param field   the {@link Token} of the field
     * @return the {@link Optional<Object>} containing the element with the property set
     * @see #trySetPropertyOnElement(Object, Object, Token, Integer)
     */
    Optional<Object> trySetPropertyOnElement(Object element, Object value, Token field) {
        return trySetPropertyOnElement(element, value, field, null);
    }

    /**
     * Sets a date on a FHIR element.
     *
     * @param element the element on which to set the date
     * @param field   the field name to set
     * @param date    the {@link LocalDate} to set on the element
     * @return the {@link Optional<Object>} of the element on which the date has been set
     */
    public Optional<Object> setDateOnElement(Object element, Token field, LocalDate date) {
        var fieldName = field.lexeme();

        return setDate(element, field, fieldName, date);
    }

    /**
     * Sets a boolean value on an element field.
     *
     * @param element the element on which to set the boolean
     * @param field   the field name
     * @param value   the value to set
     * @return the object on which the value has been set
     */
    public Optional<Object> setBooleanOnElement(Object element, Token field, Boolean value) {
        var elementClass = element.getClass();
        var fieldName = field.lexeme();
        var setterName = makeMethodName(fieldName, "set");

        try {
            var setterMethod = elementClass.getMethod(setterName, boolean.class);
            setterMethod.invoke(element, value);
            return Optional.of(element);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeError(field, String.format(WRONG_PROPERTY_TYPE_ERROR, "boolean"));
        }
    }

    /**
     * Sets a decimal on the element.
     *
     * @param element the element on which to set the decimal
     * @param field   the field name
     * @param decimal the value to set
     * @return the object on which the value has been set
     */
    public Optional<Object> setDecimalOnElement(Object element, Token field, Double decimal) {
        var elementClass = element.getClass();
        var fieldName = field.lexeme();
        var setterName = makeMethodName(fieldName, "set");

        try {
            // We check if a DecimalType does exist, if not, we try to set as double.
            var optionalMethod = getTypeMethod(elementClass, setterName);
            if (optionalMethod.isPresent()) {
                var setterMethod = optionalMethod.get();
                var decimalType = new DecimalType(decimal);
                setterMethod.invoke(element, decimalType);
            } else {
                var setterMethod = elementClass.getMethod(setterName, double.class);
                setterMethod.invoke(element, decimal);
            }
            return Optional.of(element);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeError(field, String.format(WRONG_PROPERTY_TYPE_ERROR, "decimal"));
        }
    }

    /**
     * Sets a integer on the element.
     *
     * @param element the element on which to set the integer
     * @param field   the field name
     * @param integer the value to set
     * @return the object on which the value has been set
     */
    public Optional<Object> setIntegerOnElement(Object element, Token field, Integer integer) {
        var elementClass = element.getClass();
        var fieldName = field.lexeme();
        var setterName = makeMethodName(fieldName, "set");

        try {
            // We check if a IntegerType does exist, if not, we try to set as int.
            var optionalMethod = getTypeMethod(elementClass, setterName);
            if (optionalMethod.isPresent()) {
                var setterMethod = optionalMethod.get();
                var integerType = new IntegerType(integer);
                setterMethod.invoke(element, integerType);
            } else {
                var setterMethod = elementClass.getMethod(setterName, int.class);
                setterMethod.invoke(element, integer);
            }
            return Optional.of(element);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeError(field, String.format(WRONG_PROPERTY_TYPE_ERROR, "integer"));
        }

    }

    private Optional<Method> getTypeMethod(Class<?> klass, String methodName) {
        try {
            return Optional.of(klass.getMethod(methodName, Type.class));
        } catch (NoSuchMethodError | NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private Optional<Object> tryAdd(Object element, Object value, Token field, Method accessor) {
        final String fieldName = field.lexeme();
        final var elementClass = element.getClass();

        var returnType = getActualReturnType(accessor);

        if (PrimitiveType.class.isAssignableFrom(returnType)) {
            var optionalAdder = adderMethod(elementClass, fieldName);

            if (optionalAdder.isPresent()) {
                Method adderMethod = optionalAdder.get();
                var param = adderMethod.getParameterTypes()[0];
                try {
                    if (param.equals(value.getClass())) {
                        adderMethod.invoke(element, value);

                        return Optional.of(element);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeError(field, FIELD_OR_PROPERTY_INVALID_ERROR);
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Object> trySetRequiredValue(Object element, Object value, Token field, Method accessor) {
        // Here we try to set one of them by first analyzing the return type of its getter. If it is an  Enum,
        // then we try to parse with it the uppercase version of the value. If it fails, we know the value is
        // not allowed, otherwise, we assign it.
        final String fieldName = field.lexeme();
        final String objectName = ((IElement) element).fhirType();
        final var objectClass = element.getClass();

        if (accessor.getReturnType().isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            var enumType = (Class<Enum>) accessor.getReturnType();
            var uppercaseValue = ((String) value).toUpperCase();

            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Enum enumValue = Enum.valueOf(enumType, uppercaseValue);
                String setName = makeMethodName(fieldName, "set");
                Method setterMethod = objectClass.getMethod(setName, enumValue.getClass());

                setterMethod.invoke(element, enumValue);

                return Optional.of(element);
            } catch (IllegalArgumentException | ReflectiveOperationException e) {
                var errorMessage = new StringBuilder();
                var enumConstants = enumType.getEnumConstants();
                var format = String.format("'%s' field on '%s' has required parameter " +
                        "and '%s' is invalid. Valid parameters are: ", objectName, fieldName, value);
                errorMessage.append(format);
                for (var anEnum : enumConstants) {
                    errorMessage.append(anEnum.name().toLowerCase()).append(", ");
                }
                throw new RuntimeError(field, errorMessage.toString());
            }
        }
        return Optional.empty();
    }

    private Optional<Object> tryAddToCollection(Object element, Object value, Token field, Integer index, Method
            accessor) {
        // It first tries to get the collection from the field getter, then it tests if the element at {@code index}
        // position is already present and, if yes sets it. If it could not be set, then it tries with {@code add} to
        // append a new value.
        try {
            var returnType = accessor.getReturnType();
            if (Collection.class.isAssignableFrom(returnType)) {
                Object receiver = accessor.invoke(element);
                // If it is a Collection we try to replace the element at index, if present, or we add it.
                if (elementExists(receiver, index)) {
                    Method setterMethod = receiver.getClass().getMethod("set", int.class, Object.class);
                    setterMethod.invoke(receiver, index, value);

                    return Optional.of(element);
                } else {
                    Method adderMethod = receiver.getClass().getMethod("add", Object.class);
                    Method indexOfMethod = receiver.getClass().getMethod("indexOf", Object.class);
                    adderMethod.invoke(receiver, value);
                    if (!index.equals(indexOfMethod.invoke(receiver, value))) {
                        throw new RuntimeError(field, INDEX_OUT_OF_ORDER_ERROR);
                    }

                    return Optional.of(element);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeError(field, FIELD_OR_PROPERTY_INVALID_ERROR);
        }

        return Optional.empty();
    }

    private Optional<Object> trySetDate(Object element, Object value, Token field, Method accessor) {
        var type = accessor.getReturnType();
        var fieldName = field.lexeme();

        if (Date.class.equals(type)) {
            LocalDate date = typeService.getAsLocalDate(field, value, null);

            return setDate(element, field, fieldName, date);
        }

        return Optional.empty();
    }

    private Optional<Object> trySetDecimal(Object element, Object value, Token field) {
        try {
            Double decimal = typeService.getAsDecimal((String) value, field);

            return setDecimalOnElement(element, field, decimal);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Object> trySetInteger(Object element, Object value, Token field) {
        try {
            Integer integer = typeService.getAsInteger((String) value, field);

            return setIntegerOnElement(element, field, integer);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @NotNull
    private Optional<Object> setDate(Object element, Token field, String fieldName, LocalDate date) {
        var elementClass = element.getClass();

        var dateType = new DateType(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());

        try {
            String setterName = makeMethodName(fieldName, "set") + "Element";
            Method setterMethod = elementClass.getMethod(setterName, DateType.class);
            setterMethod.invoke(element, dateType);
            return Optional.of(element);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeError(field, FIELD_OR_PROPERTY_INVALID_ERROR);
        }
    }

    private Optional<Object> trySet(Object element, Object value, Token field) {
        var elementClass = element.getClass();
        final String fieldName = field.lexeme();

        var setterMethod = setterMethod(elementClass, fieldName)
                .orElseThrow(() -> new RuntimeError(field, FIELD_OR_PROPERTY_INVALID_ERROR));

        var getterMethod = getterMethod(elementClass, fieldName);

        if (getterMethod.isPresent()) {
            var returnType = getterMethod.get().getReturnType();

            // Here we check all the possible primitive parameter types.
            if ((boolean.class.isAssignableFrom(returnType) || Boolean.class.isAssignableFrom(returnType))
                    && value instanceof String stringBoolean) {
                value = typeService.getAsBoolean(stringBoolean, field);
            }
        }

        try {
            setterMethod.invoke(element, value);

            return Optional.of(element);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @NotNull
    private Optional<Method> adderMethod(Class<?> klass, String fieldName) {
        String setterName = makeMethodName(fieldName, "add");
        return Arrays.stream(klass.getMethods())
                .filter(method -> method.getParameterCount() == 1) // If there is more than one parameter we don't know what to do
                .filter(method -> setterName.equals(method.getName()))
                .findAny();
    }

    @NotNull
    private Optional<Method> setterMethod(Class<?> klass, String fieldName) {
        String setterName = makeMethodName(fieldName, "set");
        return Arrays.stream(klass.getMethods())
                .filter(method -> method.getParameterCount() == 1) // If there is more than one parameter we don't know what to do
                .filter(method -> setterName.equals(method.getName()))
                .findAny();
    }

    @NotNull
    private Optional<Method> getterMethod(Class<?> klass, String fieldName) {
        String getterName = makeMethodName(fieldName, "get");
        String isName = makeMethodName(fieldName, "is");
        return Arrays.stream(klass.getMethods())
                .filter(method -> method.getParameterCount() == 0) // If there is more than one parameter we don't know what to do
                .filter(method -> getterName.equals(method.getName()) || isName.equals(method.getName()))
                .findAny();
    }

    private Class<?> getActualReturnType(Method method) {
        if (method.getGenericReturnType() instanceof ParameterizedType pt) {
            return (Class<?>) pt.getActualTypeArguments()[0];
        }

        return method.getReturnType();
    }

    private boolean elementExists(Object tempReceiver, Integer index) {
        try {
            var getter = tempReceiver.getClass().getMethod("get", int.class);
            var result = getter.invoke(tempReceiver, index);
            return result != null;
        } catch (Exception e) {
            return false;
        }
    }

    @NotNull
    public Optional<Method> getOptionalAccessor(String fieldName, Class<?> objectClass) {
        var getterName = makeMethodName(fieldName, "get");
        var isName = makeMethodName(fieldName, "is");
        return Arrays.stream(objectClass.getMethods())
                .filter(m -> getterName.equals(m.getName()) || isName.equals(m.getName()))
                .findAny();
    }

    @NotNull
    private String makeMethodName(String name, String prefix) {
        var firstLetter = name.substring(0, 1);
        var rest = name.substring(1);
        firstLetter = firstLetter.toUpperCase();
        return prefix + firstLetter + rest;
    }
}

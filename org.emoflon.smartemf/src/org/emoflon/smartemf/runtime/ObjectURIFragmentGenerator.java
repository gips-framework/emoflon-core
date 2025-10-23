package org.emoflon.smartemf.runtime;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.util.BasicEList;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EFactory;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EContentsEList;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.InternalEList;

/**
 * The code was taken from
 * org.eclipse.emf.ecore.impl.BasicEObjectImpl.eURIFragmentSegment(EStructuralFeature,
 * EObject)
 */
class ObjectURIFragmentGenerator {

	public static String eURIFragmentSegment(EObject base, EStructuralFeature eStructuralFeature, EObject eObject) {
		if (eStructuralFeature == null) {
			@SuppressWarnings("unchecked")
			EContentsEList.FeatureIterator<EObject> crossReferences = (EContentsEList.FeatureIterator<EObject>) ((InternalEList<?>) base
					.eCrossReferences()).basicIterator();

			for (; crossReferences.hasNext();) {
				EObject crossReference = crossReferences.next();
				if (crossReference == eObject) {
					eStructuralFeature = crossReferences.feature();
				}
			}
		}

		StringBuilder result = new StringBuilder();
		result.append('@');
		result.append(eStructuralFeature.getName());

		if (eStructuralFeature instanceof EAttribute) {
			FeatureMap featureMap = (FeatureMap) base.eGet(eStructuralFeature, false);
			for (int i = 0, size = featureMap.size(); i < size; ++i) {
				if (featureMap.getValue(i) == eObject) {
					EStructuralFeature entryFeature = featureMap.getEStructuralFeature(i);
					if (entryFeature instanceof EReference && ((EReference) entryFeature).isContainment()) {
						result.append('.');
						result.append(i);
						return result.toString();
					}
				}
			}
			result.append(".-1");
		} else if (eStructuralFeature.isMany()) {
			EList<EAttribute> eKeys = ((EReference) eStructuralFeature).getEKeys();
			if (eKeys.isEmpty()) {
				EList<?> eList = (EList<?>) base.eGet(eStructuralFeature, false);
				int index = eList.indexOf(eObject);
				result.append('.');
				result.append(index);
			} else {
				EAttribute[] eAttributes = (EAttribute[]) ((BasicEList<?>) eKeys).data();
				result.append('[');
				for (int i = 0, size = eAttributes.length; i < size; ++i) {
					EAttribute eAttribute = eAttributes[i];
					if (eAttribute == null) {
						break;
					} else {
						if (i != 0) {
							result.append(',');
						}
						result.append(eAttribute.getName());
						result.append('=');
						EDataType eDataType = eAttribute.getEAttributeType();
						EFactory eFactory = eDataType.getEPackage().getEFactoryInstance();
						if (eAttribute.isMany()) {
							List<?> values = (List<?>) eObject.eGet(eAttribute);
							result.append('[');
							if (!values.isEmpty()) {
								Iterator<?> j = values.iterator();
								eEncodeValue(result, eFactory, eDataType, j.next());
								while (j.hasNext()) {
									result.append(',');
									eEncodeValue(result, eFactory, eDataType, j.next());
								}
							}
							result.append(']');
						} else {
							eEncodeValue(result, eFactory, eDataType, eObject.eGet(eAttribute));
						}
					}
				}
				result.append(']');
			}
		}

		return result.toString();
	}

	private static final String[] ESCAPE = { "%00", "%01", "%02", "%03", "%04", "%05", "%06", "%07", "%08", "%09",
			"%0A", "%0B", "%0C", "%0D", "%0E", "%0F", "%10", "%11", "%12", "%13", "%14", "%15", "%16", "%17", "%18",
			"%19", "%1A", "%1B", "%1C", "%1D", "%1E", "%1F", "%20", null, "%22", "%23", null, "%25", "%26", "%27", null,
			null, null, null, "%2C", null, null, "%2F", null, null, null, null, null, null, null, null, null, null,
			"%3A", null, "%3C", null, "%3E", null, };

	private static void eEncodeValue(StringBuilder result, EFactory eFactory, EDataType eDataType, Object value) {
		String stringValue = eFactory.convertToString(eDataType, value);
		if (stringValue == null) {
			result.append("null");
		} else {
			int length = stringValue.length();
			result.ensureCapacity(result.length() + length + 2);
			result.append('\'');
			for (int i = 0; i < length; ++i) {
				char character = stringValue.charAt(i);
				if (character < ESCAPE.length) {
					String escape = ESCAPE[character];
					if (escape != null) {
						result.append(escape);
						continue;
					}
				}
				result.append(character);
			}
			result.append('\'');
		}
	}

	private static Object eDecodeValue(String encodedValue, EFactory eFactory, EDataType eDataType) {
		String literal = URI.decode(encodedValue);
		Object value = eFactory.createFromString(eDataType, literal);
		return value;
	}

	public static EObject eObjectForURIFragmentSegment(EObject base, String uriFragmentSegment) {
		int lastIndex = uriFragmentSegment.length() - 1;
		if (lastIndex == -1 || uriFragmentSegment.charAt(0) != '@') {
			throw new IllegalArgumentException("Expecting @ at index 0 of '" + uriFragmentSegment + "'");
		}

		char lastChar = uriFragmentSegment.charAt(lastIndex);
		if (lastChar == ']') {
			int index = uriFragmentSegment.indexOf('[');
			if (index >= 0) {
				EReference eReference = eReference(base, uriFragmentSegment.substring(1, index));
				String predicate = uriFragmentSegment.substring(index + 1, lastIndex);
				return eObjectForURIFragmentPredicate(base, predicate, eReference);
			} else {
				throw new IllegalArgumentException("Expecting [ in '" + uriFragmentSegment + "'");
			}
		} else {
			int dotIndex = -1;
			if (Character.isDigit(lastChar)) {
				dotIndex = uriFragmentSegment.lastIndexOf('.', lastIndex - 1);
				if (dotIndex >= 0) {
					EList<?> eList = (EList<?>) base
							.eGet(eStructuralFeature(base, uriFragmentSegment.substring(1, dotIndex)), false);
					int position = 0;
					try {
						position = Integer.parseInt(uriFragmentSegment.substring(dotIndex + 1));
					} catch (NumberFormatException exception) {
						throw new WrappedException(exception);
					}
					if (position < eList.size()) {
						Object result = eList.get(position);
						if (result instanceof FeatureMap.Entry) {
							result = ((FeatureMap.Entry) result).getValue();
						}
						return (EObject) result;
					}
				}
			}

			if (dotIndex < 0) {
				return (EObject) base.eGet(eStructuralFeature(base, uriFragmentSegment.substring(1)), false);
			}
		}

		return null;
	}

	private static EObject eObjectForURIFragmentPredicate(EObject base, String predicate, EReference eReference) {
		ArrayList<FeatureMap.Entry> featureMapEntries = new ArrayList<FeatureMap.Entry>();
		int length = predicate.length();
		EClass eReferenceType = eReference.getEReferenceType();
		for (int i = 0; i < length; ++i) {
			int index = requiredIndexOf(predicate, '=', i);
			EAttribute eAttribute = eAttribute(eReferenceType, predicate.substring(i, index));
			EDataType eDataType = eAttribute.getEAttributeType();
			EFactory eFactory = eDataType.getEPackage().getEFactoryInstance();
			switch (predicate.charAt(++index)) {
			case '\'': {
				int end = requiredIndexOf(predicate, '\'', ++index);
				addEntry(featureMapEntries, eAttribute,
						eDecodeValue(predicate.substring(index, end), eFactory, eDataType));
				i = end + 1;
				break;
			}
			case '"': {
				int end = requiredIndexOf(predicate, '"', ++index);
				addEntry(featureMapEntries, eAttribute,
						eDecodeValue(predicate.substring(index, end), eFactory, eDataType));
				i = end + 1;
				break;
			}
			case '[': {
				ArrayList<Object> values = new ArrayList<Object>();
				addEntry(featureMapEntries, eAttribute, values);
				LOOP: for (;;) {
					if (++index >= length) {
						throw new IllegalArgumentException(
								"Expecting ', \", ], or null at index " + index + " of '" + predicate + "'");
					}

					switch (predicate.charAt(index)) {
					case '\'': {
						int end = requiredIndexOf(predicate, '\'', ++index);
						values.add(eDecodeValue(predicate.substring(index, end), eFactory, eDataType));
						index = end + 1;
						break;
					}
					case '"': {
						int end = requiredIndexOf(predicate, '"', ++index);
						values.add(eDecodeValue(predicate.substring(index, end), eFactory, eDataType));
						index = end + 1;
						break;
					}
					case 'n': {
						++index;
						if (predicate.indexOf("ull", index) == index) {
							values.add(null);
						} else {
							throw new IllegalArgumentException(
									"Expecting null at index " + (index - 1) + " of '" + predicate + "'");
						}
						index += 3;
						break;
					}
					case ']': {
						break;
					}
					default: {
						throw new IllegalArgumentException(
								"Expecting ', \", ], or null at index " + index + " of '" + predicate + "'");
					}
					}

					if (index < length) {
						switch (predicate.charAt(index)) {
						case ',': {
							break;
						}
						case ']': {
							break LOOP;
						}
						default: {
							throw new IllegalArgumentException(
									"Expecting , or ] at index " + index + " of '" + predicate + "'");
						}
						}
					} else {
						throw new IllegalArgumentException(
								"Expecting , or ] at index " + index + " of '" + predicate + "'");
					}
				}
				i = index + 1;
				break;
			}
			case 'n': {
				++index;
				if (predicate.indexOf("ull", index) == index) {
					addEntry(featureMapEntries, eAttribute, null);
				} else {
					throw new IllegalArgumentException(
							"Expecting null at index " + (index - 1) + " of '" + predicate + "'");
				}
				i = index + 3;
				break;
			}
			default: {
				throw new IllegalArgumentException(
						"Expecting ', \", [, or null at index " + index + " of '" + predicate + "'");
			}
			}
			if (i < length) {
				if (predicate.charAt(i) != ',') {
					throw new IllegalArgumentException("Expecting , at index " + i + " of '" + predicate + "'");
				}
			} else {
				break;
			}
		}

		return eObjectForURIFragmentPredicate(base, featureMapEntries, eReference);
	}

	private static EObject eObjectForURIFragmentPredicate(EObject base, List<FeatureMap.Entry> predicate,
			EReference eReference) {
		int size = predicate.size();
		@SuppressWarnings("unchecked")
		EList<EObject> list = ((EList<EObject>) base.eGet(eReference, false));
		LOOP: for (EObject eObject : list) {
			for (int i = 0; i < size; ++i) {
				FeatureMap.Entry entry = predicate.get(i);
				Object entryValue = entry.getValue();
				EStructuralFeature entryFeature = entry.getEStructuralFeature();
				Object actualValue = eObject.eGet(entryFeature, false);
				if (entryValue == null ? actualValue != null : !entryValue.equals(actualValue)) {
					continue LOOP;
				}
			}
			return eObject;
		}
		return null;
	}

	private static EStructuralFeature eStructuralFeature(EObject base, String name) throws IllegalArgumentException {
		EStructuralFeature eStructuralFeature = base.eClass().getEStructuralFeature(name);
		if (eStructuralFeature == null)
			throw new IllegalArgumentException("The feature '" + name + "' is not a valid feature");
		return eStructuralFeature;
	}

	private static EReference eReference(EObject base, String name) throws IllegalArgumentException {
		EStructuralFeature eStructuralFeature = base.eClass().getEStructuralFeature(name);
		if (eStructuralFeature instanceof EReference)
			return (EReference) eStructuralFeature;

		throw new IllegalArgumentException("The feature '" + name + "' is not a valid reference");
	}

	private static EAttribute eAttribute(EClass eClass, String name) throws IllegalArgumentException {
		EStructuralFeature eStructuralFeature = eClass.getEStructuralFeature(name);
		if (eStructuralFeature instanceof EAttribute) {
			return (EAttribute) eStructuralFeature;
		}
		throw new IllegalArgumentException("The feature '" + name + "' is not a valid attribute");
	}

	private static final void addEntry(List<FeatureMap.Entry> featureMapEntries, final EAttribute eAttribute,
			final Object value) {
		featureMapEntries.add(new FeatureMap.Entry() {
			public EStructuralFeature getEStructuralFeature() {
				return eAttribute;
			}

			public Object getValue() {
				return value;
			}
		});
	}

	private static final int requiredIndexOf(String string, char character, int start) {
		int index = string.indexOf(character, start);
		if (index < 0) {
			throw new IllegalArgumentException(
					"Expecting " + character + " at or after index " + start + " of '" + string + "'");
		} else {
			return index;
		}
	}
}

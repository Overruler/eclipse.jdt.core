/*******************************************************************************
 * Copyright (c) 2013 Timo Kinnunen and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * This is an implementation of an early-draft specification developed under the Java
 * Community Process (JCP) and is made available for testing and evaluation purposes
 * only. The code is not compatible with any specification of the JCP.
 *
 * Contributors:
 *     Timo Kinnunen - Contributions for 
 *     							Bug 420953 - [subwords] Constructors that don't match prefix not found
 *******************************************************************************/
package org.eclipse.jdt.internal.codeassist;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.util.SuffixConstants;
import org.eclipse.jdt.internal.core.ClassFile;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.NameLookup;
import org.eclipse.jdt.internal.core.Openable;

class SuperOrSubtypesCompletionHelper {
	private static final int MAX_RESULTS = 100;

	static List<ReferenceBinding> findAdditionalExpectedTypes(ITypeRoot typeRoot, LookupEnvironment lookupEnvironment,
			NameLookup nameLookup, TypeBinding[] expectedTypes, boolean supertypeOnly, boolean subtypeOnly,
			IProgressMonitor monitor) throws JavaModelException {
		List<ReferenceBinding> referenceTypes = new ArrayList<ReferenceBinding>();
		for (TypeBinding typeBinding : expectedTypes) {
			if (typeBinding instanceof ReferenceBinding) {
				referenceTypes.add((ReferenceBinding) typeBinding);
			}
		}
		List<ReferenceBinding> additionalReferenceTypes = new ArrayList<ReferenceBinding>();
		for (ReferenceBinding referenceBinding : referenceTypes) {
			if (referenceBinding.compoundName == null
					|| CharOperation.toString(referenceBinding.compoundName).equals("java.lang.Object")) { //$NON-NLS-1$
				continue;
			}
			IType[] types = getExpectedSuperOrSubtypes(referenceBinding, supertypeOnly, subtypeOnly, monitor,
					nameLookup, typeRoot);
			for (IType iType : types) {
				ReferenceBinding referenceBindingFromIType = getReferenceBindingFromIType(iType, lookupEnvironment);
				additionalReferenceTypes.add(referenceBindingFromIType);
				if (additionalReferenceTypes.size() > MAX_RESULTS) {
					return additionalReferenceTypes;
				}
			}
		}
		return additionalReferenceTypes;
	}

	private static IType[] getExpectedSuperOrSubtypes(ReferenceBinding refBinding, boolean supertypeOnly,
			boolean subtypeOnly, IProgressMonitor monitor, NameLookup nameLookup, ITypeRoot typeRoot)
			throws JavaModelException {
		IType type = getITypeFromReferenceBinding(refBinding, nameLookup, typeRoot);
		if(type == null) {
			return new IType[0];
		}

		IType[] types;
		if (supertypeOnly) {
			ITypeHierarchy typeHierarchy = type.newSupertypeHierarchy(monitor);
			types = typeHierarchy.getAllSupertypes(type);
		} else if (subtypeOnly) {
			ITypeHierarchy typeHierarchy = type.newTypeHierarchy(monitor);
			types = typeHierarchy.getAllSubtypes(type);
		} else {
			IType[] types2 = { type };
			types = types2;
		}
		return types;
	}

	private static ReferenceBinding getReferenceBindingFromIType(IType iType, LookupEnvironment lookupEnvironment) {
		List<char[]> result = new ArrayList<char[]>();
		String[] split = iType.getFullyQualifiedName('.').split("\\."); //$NON-NLS-1$
		for (int i = 0; i < split.length; i++) {
			String string = split[i];
			char[] value = string.toCharArray();
			result.add(value);
		}
		char[][] compoundName = result.toArray(new char[][] {});

		ReferenceBinding iTypeRefBinding = lookupEnvironment.getType(compoundName);
		return iTypeRefBinding;
	}

	private static IType getITypeFromReferenceBinding(ReferenceBinding typeBinding, NameLookup nameLookup,
			ITypeRoot typeRoot) {
		if (typeBinding == null || !typeBinding.isValidBinding())
			return null;

		char[] packageName = typeBinding.qualifiedPackageName();

		IPackageFragment[] pkgs = nameLookup.findPackageFragments(
				(packageName == null || packageName.length == 0) ? IPackageFragment.DEFAULT_PACKAGE_NAME : new String(
						packageName), false);

		// iterate type lookup in each package fragment
		char[] sourceName = typeBinding.qualifiedSourceName();
		String typeName = new String(sourceName);
		int acceptFlag = 0;
		if (typeBinding.isAnnotationType()) {
			acceptFlag = NameLookup.ACCEPT_ANNOTATIONS;
		} else if (typeBinding.isEnum()) {
			acceptFlag = NameLookup.ACCEPT_ENUMS;
		} else if (typeBinding.isInterface()) {
			acceptFlag = NameLookup.ACCEPT_INTERFACES;
		} else if (typeBinding.isClass()) {
			acceptFlag = NameLookup.ACCEPT_CLASSES;
		}
		if (pkgs != null) {
			for (int i = 0, length = pkgs.length; i < length; i++) {
				IType type = nameLookup.findType(typeName, pkgs[i],  false,  acceptFlag, false, true/*consider secondary types*/);
				if (type != null) return type;
			}
		}

		// search inside enclosing element
		char[][] qualifiedName = CharOperation.splitOn('.', sourceName);
		int length = qualifiedName.length;
		if (length == 0)
			return null;

		IType type = createTypeHandle(new String(qualifiedName[0]), typeRoot); // find the top-level type
		if (type == null)
			return null;

		for (int i = 1; i < length; i++) {
			type = type.getType(new String(qualifiedName[i]));
			if (type == null)
				return null;
		}
		if (type.exists())
			return type;
		return null;
	}

	private static IType createTypeHandle(String simpleTypeName, ITypeRoot typeRoot) {
		if (!(typeRoot instanceof Openable))
			return null;
		Openable openable = (Openable) typeRoot;
		if (openable instanceof CompilationUnit)
			return ((CompilationUnit) openable).getType(simpleTypeName);

		IType binaryType = ((ClassFile) openable).getType();
		String binaryTypeQualifiedName = binaryType.getTypeQualifiedName();
		if (simpleTypeName.equals(binaryTypeQualifiedName))
			return binaryType; // answer only top-level types, sometimes the classFile is for a member/local type

		// type name may be null for anonymous (see bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=164791)
		String classFileName = simpleTypeName.length() == 0 ? binaryTypeQualifiedName : simpleTypeName;
		IClassFile classFile = binaryType.getPackageFragment().getClassFile(
				classFileName + SuffixConstants.SUFFIX_STRING_class);
		return classFile.getType();
	}
}

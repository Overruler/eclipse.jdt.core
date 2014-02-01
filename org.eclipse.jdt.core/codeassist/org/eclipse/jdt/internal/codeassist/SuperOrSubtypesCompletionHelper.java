/*******************************************************************************
 * Copyright (c) 2013, 2014 Timo Kinnunen and others.
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
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
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
	private static final long SUBTYPE_TIMEOUT = 60 * 1000;
	private static final int MAX_RESULTS = 100;

	private static ConcurrentHashMap<String, Long> skippedSlowSubTypes = new ConcurrentHashMap<>(16, 0.75f, 1);

	static ArrayList<ReferenceBinding> findAdditionalExpectedTypes(IJavaProject javaProject, ITypeRoot typeRoot,
			LookupEnvironment lookupEnvironment, NameLookup nameLookup, TypeBinding[] expectedTypes,
			boolean supertypeOnly, boolean subtypeOnly, IProgressMonitor monitor) throws JavaModelException {
		List<ReferenceBinding> referenceTypes = new ArrayList<>();
		for (TypeBinding typeBinding : expectedTypes) {
			if (typeBinding instanceof ReferenceBinding) {
				referenceTypes.add((ReferenceBinding) typeBinding);
			}
		}
		ArrayList<ReferenceBinding> additionalReferenceTypes = new ArrayList<>();
		for (ReferenceBinding referenceBinding : referenceTypes) {
			if (referenceBinding.compoundName == null) {
				continue;
			}
			String name = CharOperation.toString(referenceBinding.compoundName);
			if (name.equals("java.lang.Object")) { //$NON-NLS-1$
				continue;
			}
			IType[] types = getExpectedSuperOrSubtypes(javaProject, typeRoot, referenceBinding, nameLookup,
					supertypeOnly, subtypeOnly, monitor);
			for (IType iType : types) {
				ReferenceBinding referenceBindingFromIType = getReferenceBindingFromIType(iType, lookupEnvironment);
				Long retryTime = skippedSlowSubTypes.get(name);
				if (retryTime == null || System.currentTimeMillis() > retryTime) {
					if (retryTime != null) {
						skippedSlowSubTypes.remove(name);
					}
					try {
						additionalReferenceTypes.add(referenceBindingFromIType);
						if (additionalReferenceTypes.size() > MAX_RESULTS) {
							return additionalReferenceTypes;
						}
					} catch (OperationCanceledException e) {
						monitor.setCanceled(false);
						skippedSlowSubTypes.put(name, System.currentTimeMillis() + SUBTYPE_TIMEOUT);
						warn("Type hierarchy too slow, giving 1 min penalty to " + name, e); //$NON-NLS-1$
						return additionalReferenceTypes;
					}
				}
			}
		}
		return additionalReferenceTypes;
	}

	private static IType[] getExpectedSuperOrSubtypes(IJavaProject project, ITypeRoot typeRoot,
			ReferenceBinding refBinding, NameLookup nameLookup, boolean supertypeOnly, boolean subtypeOnly,
			IProgressMonitor monitor) throws JavaModelException {
		IType type = getITypeFromReferenceBinding(refBinding, nameLookup, typeRoot);
		if (type == null || supertypeOnly == subtypeOnly) {
			return new IType[0];
		}

		if (supertypeOnly) {
			ITypeHierarchy typeHierarchy = type.newSupertypeHierarchy(monitor);
			return typeHierarchy.getAllSupertypes(type);
		}
		ITypeHierarchy typeHierarchy = type.newTypeHierarchy(project, monitor);
		return typeHierarchy.getAllSubtypes(type);
	}

	private static ReferenceBinding getReferenceBindingFromIType(IType iType, LookupEnvironment lookupEnvironment) {
		List<char[]> result = new ArrayList<char[]>();
		String[] split = iType.getFullyQualifiedName('.').split("\\."); //$NON-NLS-1$
		for (int i = 0; i < split.length; i++) {
			String string = split[i];
			char[] value = string.toCharArray();
			result.add(value);
		}
		char[][] compoundName = result.toArray(new char[result.size()][]);

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
				IType type = nameLookup
						.findType(typeName, pkgs[i], false, acceptFlag, true/* consider secondary types */);
				if (type != null)
					return type;
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

	private static void warn(String message, Exception e) {
		Plugin plugin = JavaCore.getPlugin();
		String symbolicName = plugin.getBundle().getSymbolicName();
		plugin.getLog().log(new Status(IStatus.WARNING, symbolicName, message, e));
	}
}

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.LinkedHashMap
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

plugins {
    id("com.android.library")
}

android {
    namespace = "com.universal.virtualcore"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86_64"))
        }

        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17 -fexceptions -frtti")
                // Android 16 mandatory 16KB page alignment flag
                arguments("-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(files("libs/alok-stubs.jar"))
    implementation("androidx.core:core:1.13.1")
    implementation("androidx.annotation:annotation:1.8.0")
}

afterEvaluate {
    tasks.findByName("bundleReleaseAar")?.doLast {
        val aarFile = file("build/outputs/aar/core-release.aar")
        val stubsJar = file("libs/alok-stubs.jar")
        if (!aarFile.exists() || !stubsJar.exists()) return@doLast

        val tempAar = file("${aarFile.absolutePath}.tmp")
        val mergedClasses = LinkedHashMap<String, ByteArray>()

        val frameworkStubPrefixes = listOf(
            "com/universal/virtualcore/R$",
            "android/content/pm/PackageParser",
            "android/content/pm/PackageUserState",
            "android/content/pm/ManifestDigest",
            "android/content/pm/VerifierInfo",
            "android/os/SELinux",
            "android/os/ServiceManager",
            "android/os/ParcelableException",
            "android/content/SyncInfo",
            "android/content/SyncStatusInfo",
            "android/location/LocationRequest"
        )

        val accountHookMap = mapOf(
            "org/alok/engine/fake/service/IAccountManagerProxy\$addAccount.class" to "handleAddAccount",
            "org/alok/engine/fake/service/IAccountManagerProxy\$addAccountAsUser.class" to "handleAddAccount",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAccounts.class" to "handleGetAccounts",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAccountsAsUser.class" to "handleGetAccountsAsUser",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAccountsByType.class" to "handleGetAccountsByType",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAccountsByTypeForPackage.class" to "handleGetAccountsByTypeForPackage",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAccountsByFeatures.class" to "handleGetAccountsByFeatures",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAccountByTypeAndFeatures.class" to "handleGetAccountByTypeAndFeatures",
            "org/alok/engine/fake/service/IAccountManagerProxy\$hasFeatures.class" to "handleHasFeatures",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAuthToken.class" to "handleGetAuthToken",
            "org/alok/engine/fake/service/IAccountManagerProxy\$getAuthenticatorTypes.class" to "handleGetAuthenticatorTypes"
        )

        // 1. Read all runtime classes from stubsJar (excluding compile-time framework Stub! classes and R$ classes)
        ZipInputStream(stubsJar.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".class")) {
                    val isFrameworkStub = entry.name == "com/universal/virtualcore/R.class" ||
                            frameworkStubPrefixes.any { entry.name.startsWith(it) }
                    if (!isFrameworkStub) {
                        val rawBytes = zis.readBytes()
                        if (entry.name == "org/alok/engine/Alok.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "startLogcat" && descriptor == "()V") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitInsn(Opcodes.RETURN)
                                                mv.visitMaxs(0, 1)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    if (name == "installSystemHooks" && descriptor == "()V") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitInsn(Opcodes.RETURN)
                                                mv.visitMaxs(0, 0)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    if (name == "initNotificationManager" && descriptor == "()V") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitInsn(Opcodes.RETURN)
                                                mv.visitMaxs(0, 1)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    if (name == "isValidProcessState" && descriptor == "()Z") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitInsn(Opcodes.ICONST_1)
                                                mv.visitInsn(Opcodes.IRETURN)
                                                mv.visitMaxs(1, 1)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    if (name == "onBeforeMainLaunchApk" && descriptor == "(Ljava/lang/String;I)V") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitCode() {
                                                super.visitCode()
                                                super.visitVarInsn(Opcodes.ALOAD, 1)
                                                super.visitVarInsn(Opcodes.ILOAD, 2)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/AlokDexInjector",
                                                    "ensureInstalledBeforeLaunch",
                                                    "(Ljava/lang/String;I)V",
                                                    false
                                                )
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed org/alok/engine/Alok.class: neutralized startLogcat, installSystemHooks, initNotificationManager, fixed isValidProcessState & onBeforeMainLaunchApk!")
                        } else if (entry.name == "org/alok/engine/fake/frameworks/BPackageManager.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "shouldUseFallbackMode" && descriptor == "()Z") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitInsn(Opcodes.ICONST_0)
                                                mv.visitInsn(Opcodes.IRETURN)
                                                mv.visitMaxs(1, 1)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    if (name == "isInstalledFallback" && descriptor == "(Ljava/lang/String;)Z") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitInsn(Opcodes.ICONST_0)
                                                mv.visitInsn(Opcodes.IRETURN)
                                                mv.visitMaxs(1, 2)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    if (name == "getLaunchIntentForPackage" && descriptor == "(Ljava/lang/String;I)Landroid/content/Intent;") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitCode() {
                                                super.visitCode()
                                                super.visitVarInsn(Opcodes.ALOAD, 1)
                                                super.visitVarInsn(Opcodes.ILOAD, 2)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/AlokDexInjector",
                                                    "ensureInstalledBeforeLaunch",
                                                    "(Ljava/lang/String;I)V",
                                                    false
                                                )
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed org/alok/engine/fake/frameworks/BPackageManager.class: disabled broken host fallback mode and added auto-install before launch!")
                        } else if (entry.name == "org/alok/engine/fake/service/IPackageManagerProxy.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "isFacebookAppIntent" && descriptor == "(Landroid/content/Intent;)Z") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitVarInsn(Opcodes.ALOAD, 0)
                                                mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/SocialLoginManager",
                                                    "shouldBlockSocialIntent",
                                                    "(Landroid/content/Intent;)Z",
                                                    false
                                                )
                                                mv.visitInsn(Opcodes.IRETURN)
                                                mv.visitMaxs(1, 1)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed IPackageManagerProxy.class for Facebook/Twitter/Discord smart intent resolution!")
                        } else if (entry.name == "org/alok/engine/fake/service/IPackageManagerProxy\$QueryIntentActivities.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    return object : MethodVisitor(Opcodes.ASM9, mv) {
                                        override fun visitMethodInsn(
                                            opcode: Int,
                                            owner: String,
                                            mName: String,
                                            mDesc: String,
                                            isInterface: Boolean
                                        ) {
                                            if (opcode == Opcodes.INVOKEINTERFACE && owner == "java/util/Set" && mName == "contains" && mDesc == "(Ljava/lang/Object;)Z") {
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/SocialLoginManager",
                                                    "shouldFilterActivityPackage",
                                                    "(Ljava/util/Set;Ljava/lang/Object;)Z",
                                                    false
                                                )
                                            } else {
                                                super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface)
                                            }
                                        }
                                    }
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                        } else if (entry.name == "org/alok/engine/fake/service/IPackageManagerProxy\$GetPackageInfo.class" ||
                                   entry.name == "org/alok/engine/fake/service/IPackageManagerProxy\$GetApplicationInfo.class") {
                            val targetMethodName = if (entry.name.endsWith("GetPackageInfo.class")) "hookGetPackageInfo" else "hookGetApplicationInfo"
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "hook" && descriptor == "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitVarInsn(Opcodes.ALOAD, 1)
                                                mv.visitVarInsn(Opcodes.ALOAD, 2)
                                                mv.visitVarInsn(Opcodes.ALOAD, 3)
                                                mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/AlokDexInjector",
                                                    targetMethodName,
                                                    "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
                                                    false
                                                )
                                                mv.visitInsn(Opcodes.ARETURN)
                                                mv.visitMaxs(3, 4)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed ${entry.name} -> AlokDexInjector.$targetMethodName!")
                        } else if (entry.name == "org/alok/engine/fake/service/WebViewProxy\$Constructor.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "configureWebView" && descriptor == "(Landroid/webkit/WebView;)V") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitInsn(opcode: Int) {
                                                if (opcode == Opcodes.RETURN) {
                                                    super.visitVarInsn(Opcodes.ALOAD, 1)
                                                    super.visitMethodInsn(
                                                        Opcodes.INVOKESTATIC,
                                                        "com/universal/virtualcore/core/SocialLoginManager",
                                                        "configureWebViewForOAuth",
                                                        "(Landroid/webkit/WebView;)V",
                                                        false
                                                    )
                                                }
                                                super.visitInsn(opcode)
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed WebViewProxy\$Constructor.class: sanitized User-Agent and enabled OAuth cookies!")
                        } else if (entry.name == "org/alok/engine/fake/service/ActivityManagerCommonProxy\$StartActivity.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "hook") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitMethodInsn(
                                                opcode: Int,
                                                owner: String,
                                                mName: String,
                                                mDesc: String,
                                                isInterface: Boolean
                                            ) {
                                                if (opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/reflect/Method" && mName == "invoke") {
                                                    super.visitMethodInsn(
                                                        Opcodes.INVOKESTATIC,
                                                        "com/universal/virtualcore/core/SocialLoginManager",
                                                        "invokeHostStartActivityOrOAuth",
                                                        "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                                                        false
                                                    )
                                                } else {
                                                    super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface)
                                                }
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed ActivityManagerCommonProxy\$StartActivity.class: enabled in-process OAuth WebView dialog!")
                        } else if (entry.name == "org/alok/engine/fake/service/IActivityManagerProxy\$GetContentProvider.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visit(
                                    version: Int,
                                    access: Int,
                                    name: String,
                                    signature: String?,
                                    superName: String?,
                                    interfaces: Array<out String>?
                                ) {
                                    super.visit(Opcodes.V1_6, access, name, signature, superName, interfaces)
                                }

                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "hook") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitCode() {
                                                super.visitCode()
                                                super.visitVarInsn(Opcodes.ALOAD, 1)
                                                super.visitVarInsn(Opcodes.ALOAD, 2)
                                                super.visitVarInsn(Opcodes.ALOAD, 3)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/GmsAuthorityRouter",
                                                    "interceptGetContentProvider",
                                                    "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
                                                    false
                                                )
                                                val proceedLabel = org.objectweb.asm.Label()
                                                super.visitInsn(Opcodes.DUP)
                                                super.visitJumpInsn(Opcodes.IFNULL, proceedLabel)
                                                super.visitInsn(Opcodes.ARETURN)
                                                super.visitLabel(proceedLabel)
                                                super.visitInsn(Opcodes.POP)
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, ClassReader.SKIP_FRAMES)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed IActivityManagerProxy\$GetContentProvider.class: hooked GMS & external authority router!")
                        } else if (accountHookMap.containsKey(entry.name)) {
                            val targetMethod = accountHookMap[entry.name]!!
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "hook") {
                                        return object : MethodVisitor(Opcodes.ASM9, null) {
                                            override fun visitCode() {
                                                mv.visitCode()
                                                mv.visitVarInsn(Opcodes.ALOAD, 1)
                                                mv.visitVarInsn(Opcodes.ALOAD, 2)
                                                mv.visitVarInsn(Opcodes.ALOAD, 3)
                                                mv.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/auth/VirtualAccountHook",
                                                    targetMethod,
                                                    "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;",
                                                    false
                                                )
                                                mv.visitInsn(Opcodes.ARETURN)
                                                mv.visitMaxs(3, 4)
                                                mv.visitEnd()
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed ${entry.name} -> VirtualAccountHook.$targetMethod!")
                        } else if (entry.name == "org/alok/engine/core/IOCore.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "redirectPath" && descriptor == "(Ljava/lang/String;)Ljava/lang/String;") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitCode() {
                                                super.visitCode()
                                                super.visitVarInsn(Opcodes.ALOAD, 0)
                                                super.visitVarInsn(Opcodes.ALOAD, 1)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/virtual/VirtualIOHook",
                                                    "redirectPath",
                                                    "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/String;",
                                                    false
                                                )
                                                val proceedLabel = org.objectweb.asm.Label()
                                                super.visitInsn(Opcodes.DUP)
                                                super.visitJumpInsn(Opcodes.IFNULL, proceedLabel)
                                                super.visitInsn(Opcodes.ARETURN)
                                                super.visitLabel(proceedLabel)
                                                super.visitInsn(Opcodes.POP)
                                            }
                                        }
                                    }
                                    if (name == "enableRedirect" && descriptor == "(Landroid/content/Context;)V") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitInsn(opcode: Int) {
                                                if (opcode == Opcodes.RETURN) {
                                                    super.visitVarInsn(Opcodes.ALOAD, 0)
                                                    super.visitVarInsn(Opcodes.ALOAD, 1)
                                                    super.visitMethodInsn(
                                                        Opcodes.INVOKESTATIC,
                                                        "com/universal/virtualcore/virtual/VirtualIOHook",
                                                        "onEnableRedirect",
                                                        "(Ljava/lang/Object;Landroid/content/Context;)V",
                                                        false
                                                    )
                                                }
                                                super.visitInsn(opcode)
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed IOCore.class: hooked redirectPath & enableRedirect for OBB passthrough!")
                        } else if (entry.name == "org/alok/engine/fake/hook/HookManager.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "init" && descriptor == "()V") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            private var afterWindowManagerInit = false
                                            override fun visitMethodInsn(
                                                opcode: Int,
                                                owner: String,
                                                mName: String,
                                                mDesc: String,
                                                isInterface: Boolean
                                            ) {
                                                super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface)
                                                if (opcode == Opcodes.INVOKESPECIAL && owner == "org/alok/engine/fake/service/IWindowManagerProxy" && mName == "<init>") {
                                                    afterWindowManagerInit = true
                                                } else if (afterWindowManagerInit && opcode == Opcodes.INVOKEVIRTUAL && owner == "org/alok/engine/fake/hook/HookManager" && mName == "addInjector") {
                                                    afterWindowManagerInit = false
                                                    super.visitVarInsn(Opcodes.ALOAD, 0)
                                                    super.visitTypeInsn(Opcodes.NEW, "org/alok/engine/fake/service/IInputMethodManagerProxy")
                                                    super.visitInsn(Opcodes.DUP)
                                                    super.visitMethodInsn(
                                                        Opcodes.INVOKESPECIAL,
                                                        "org/alok/engine/fake/service/IInputMethodManagerProxy",
                                                        "<init>",
                                                        "()V",
                                                        false
                                                    )
                                                    super.visitMethodInsn(
                                                        Opcodes.INVOKEVIRTUAL,
                                                        "org/alok/engine/fake/hook/HookManager",
                                                        "addInjector",
                                                        "(Lorg/alok/engine/fake/hook/IInjectHook;)V",
                                                        false
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed HookManager.class: registered IInputMethodManagerProxy for keyboard/IME support!")
                        } else if (entry.name == "org/alok/engine/app/BActivityThread.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "injectSplitDexPaths" && descriptor == "(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;)V") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitCode() {
                                                super.visitCode()
                                                super.visitVarInsn(Opcodes.ALOAD, 0)
                                                super.visitVarInsn(Opcodes.ALOAD, 1)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/AlokDexInjector",
                                                    "injectLegacyAndSplitDex",
                                                    "(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;)V",
                                                    false
                                                )
                                            }
                                        }
                                    }
                                    if (name == "handleBindApplication" && descriptor == "(Ljava/lang/String;Ljava/lang/String;)V") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitCode() {
                                                super.visitCode()
                                                super.visitVarInsn(Opcodes.ALOAD, 1)
                                                super.visitVarInsn(Opcodes.ALOAD, 2)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/AlokDexInjector",
                                                    "onBeforeBindApplication",
                                                    "(Ljava/lang/String;Ljava/lang/String;)V",
                                                    false
                                                )
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed BActivityThread.class: hooked injectSplitDexPaths & handleBindApplication!")
                        } else if (entry.name == "org/alok/engine/core/system/pm/PackageManagerCompat.class") {
                            val cr = ClassReader(rawBytes)
                            val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                            cr.accept(object : ClassVisitor(Opcodes.ASM9, cw) {
                                override fun visitMethod(
                                    access: Int,
                                    name: String,
                                    descriptor: String,
                                    signature: String?,
                                    exceptions: Array<out String>?
                                ): MethodVisitor {
                                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                                    if (name == "generateApplicationInfo" && descriptor == "(Lorg/alok/engine/core/system/pm/BPackage;ILorg/alok/engine/core/system/pm/BPackageUserState;I)Landroid/content/pm/ApplicationInfo;") {
                                        return object : MethodVisitor(Opcodes.ASM9, mv) {
                                            override fun visitCode() {
                                                super.visitCode()
                                                super.visitVarInsn(Opcodes.ALOAD, 0)
                                                super.visitMethodInsn(
                                                    Opcodes.INVOKESTATIC,
                                                    "com/universal/virtualcore/core/AlokDexInjector",
                                                    "repairBPackageIfNeeded",
                                                    "(Lorg/alok/engine/core/system/pm/BPackage;)V",
                                                    false
                                                )
                                            }

                                            override fun visitInsn(opcode: Int) {
                                                if (opcode == Opcodes.ARETURN) {
                                                    super.visitVarInsn(Opcodes.ALOAD, 0)
                                                    super.visitMethodInsn(
                                                        Opcodes.INVOKESTATIC,
                                                        "com/universal/virtualcore/core/AlokDexInjector",
                                                        "postFixGeneratedAppInfo",
                                                        "(Landroid/content/pm/ApplicationInfo;Lorg/alok/engine/core/system/pm/BPackage;)Landroid/content/pm/ApplicationInfo;",
                                                        false
                                                    )
                                                }
                                                super.visitInsn(opcode)
                                            }
                                        }
                                    }
                                    return mv
                                }
                            }, 0)
                            mergedClasses[entry.name] = cw.toByteArray()
                            println("Bytecode transformed PackageManagerCompat.class: hooked generateApplicationInfo for stale APK path repair & nativeLibraryDir healing!")
                        } else {
                            mergedClasses[entry.name] = rawBytes
                        }
                    }
                }
                entry = zis.nextEntry
            }
        }

        // 2. Read existing AAR entries
        var existingClassesJarBytes: ByteArray? = null
        val otherAarEntries = LinkedHashMap<String, ByteArray>()

        ZipInputStream(aarFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zis.readBytes()
                    if (entry.name == "classes.jar") {
                        existingClassesJarBytes = bytes
                    } else if (!entry.name.startsWith("libs/")) {
                        otherAarEntries[entry.name] = bytes
                    }
                }
                entry = zis.nextEntry
            }
        }

        // 3. Overlay compiled classes from existing classes.jar
        if (existingClassesJarBytes != null) {
            ZipInputStream(existingClassesJarBytes!!.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        mergedClasses[entry.name] = zis.readBytes()
                    }
                    entry = zis.nextEntry
                }
            }
        }

        // 4. Package unified classes.jar
        val unifiedClassesJarBaos = ByteArrayOutputStream()
        JarOutputStream(unifiedClassesJarBaos).use { jos ->
            for ((name, bytes) in mergedClasses) {
                jos.putNextEntry(JarEntry(name))
                jos.write(bytes)
                jos.closeEntry()
            }
        }
        val unifiedClassesJarBytes = unifiedClassesJarBaos.toByteArray()

        // 5. Write final unified AAR
        ZipOutputStream(tempAar.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry("classes.jar"))
            zos.write(unifiedClassesJarBytes)
            zos.closeEntry()

            for ((name, bytes) in otherAarEntries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }

        tempAar.copyTo(aarFile, overwrite = true)
        tempAar.delete()

        // Also copy to root Core.aar and Downloads/Core.aar
        aarFile.copyTo(rootProject.file("Core.aar"), overwrite = true)
        val downloads = File(System.getProperty("user.home"), "Downloads/Core.aar")
        if (downloads.parentFile.exists()) {
            aarFile.copyTo(downloads, overwrite = true)
        }
        println("Successfully created unified Core.aar (${aarFile.length()} bytes) with ${mergedClasses.size} classes in classes.jar!")
    }
}

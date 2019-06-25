package com.intellij.styledComponents

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.injected.MultiHostRegistrarImpl
import com.intellij.psi.impl.source.tree.injected.Place
import com.intellij.psi.xml.XmlAttributeValue
import org.jetbrains.plugins.less.LESSLanguage

const val COMPONENT_PROPS_PREFIX = "div {"
const val COMPONENT_PROPS_SUFFIX = "}"

class StyledComponentsInjector : MultiHostInjector {
    companion object {
        private val styledPattern = withNameStartingWith(listOf("styled"))
        private val builtinPlaces: List<PlaceInfo> = listOf(
                PlaceInfo(taggedTemplate(PlatformPatterns.or(styledPattern,
                        PlatformPatterns.psiElement(JSExpression::class.java)
                                .withFirstChild(styledPattern))), COMPONENT_PROPS_PREFIX, COMPONENT_PROPS_SUFFIX),
                PlaceInfo(jsxAttribute("css"), COMPONENT_PROPS_PREFIX, COMPONENT_PROPS_SUFFIX),
                PlaceInfo(taggedTemplate(withReferenceName("extend")), COMPONENT_PROPS_PREFIX, COMPONENT_PROPS_SUFFIX),
                PlaceInfo(taggedTemplate(callExpression().withChild(withReferenceName("attrs"))), COMPONENT_PROPS_PREFIX, COMPONENT_PROPS_SUFFIX),
                PlaceInfo(taggedTemplate("css"), COMPONENT_PROPS_PREFIX, COMPONENT_PROPS_SUFFIX),
                PlaceInfo(taggedTemplate("injectGlobal")),
                PlaceInfo(taggedTemplate("createGlobalStyle")),
                PlaceInfo(taggedTemplate("keyframes"), "@keyframes foo {", "}")
        )
    }

    override fun elementsToInjectIn(): MutableList<out Class<out PsiElement>> {
        return mutableListOf(JSLiteralExpression::class.java, XmlAttributeValue::class.java)
    }

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, injectionHost: PsiElement) {
        if (injectionHost !is PsiLanguageInjectionHost)
            return
        val customInjections = CustomInjectionsConfiguration.instance(injectionHost.project)
        val acceptedPattern = builtinPlaces.find { (elementPattern) -> elementPattern.accepts(injectionHost) }
                ?: customInjections.getInjectionPlaces().find { (elementPattern) -> elementPattern.accepts(injectionHost) }
        if (acceptedPattern != null) {
            val stringPlaces = getInjectionPlaces(injectionHost)
            if (stringPlaces.isEmpty())
                return
            registrar.startInjecting(LESSLanguage.INSTANCE)
            stringPlaces.forEachIndexed { index, (prefix, range, suffix) ->
                val thePrefix = if (index == 0) acceptedPattern.prefix + prefix.orEmpty() else prefix
                val theSuffix = if (index == stringPlaces.size - 1) suffix.orEmpty() + acceptedPattern.suffix else suffix
                registrar.addPlace(thePrefix, theSuffix, injectionHost, range)
            }
            registrar.doneInjecting()
            val result = getInjectionResult(registrar) ?: return
            val injectedFile = result.second
            val injectedFileRanges = result.first.map { TextRange(it.range.startOffset, it.range.endOffset - it.suffix.length) }

            if (injectedFileRanges.size > 1) {
                injectedFile.putUserData(INJECTED_FILE_RANGES_KEY, injectedFileRanges)
            }
        }
    }

    private fun getInjectionResult(registrar: MultiHostRegistrar): Pair<Place, PsiFile>? {
        val result = (registrar as MultiHostRegistrarImpl).result
        return if (result == null || result.isEmpty()) null
        else result[result.size - 1]
    }
}

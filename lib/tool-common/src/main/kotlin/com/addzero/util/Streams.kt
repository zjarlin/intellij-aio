package com.addzero.util

import cn.hutool.core.collection.CollUtil
import com.addzero.common.kt_util.removeIf
import java.util.*
import java.util.function.BiFunction
import java.util.function.BinaryOperator
import java.util.function.Function
import java.util.function.Predicate
import java.util.stream.Collectors
import java.util.stream.IntStream
import java.util.stream.Stream

/**
 * 流工具类
 *
 * @author zjarlin
 * @since 2022/4/27 10:53 PM
 */
@Suppress("unused")
interface Streams {
    companion object {


        @SafeVarargs
        fun <T> unique(
            list: List<T>,
            binaryOperator: BinaryOperator<T> = BinaryOperator { a, _ -> a },  // 默认为保留第一个元素
            vararg keyExtractors: (T) -> Any?,
        ): List<T> {
            val map: Map<List<Any?>, T> = list
                .stream()
                .collect(
                    Collectors.toMap(
                        { item: T ->
                            keyExtractors.map { extractor -> extractor(item) } // 提取关键字段列表
                        },
                        { item: T -> item },
                        binaryOperator,
                        { linkedMapOf() }
                    )
                )

            return ArrayList(map.values)
        }

        fun <T, R> partition(
            collection: Collection<T>,
            getFun: Function<T, R>,
            vararg predicates: Predicate<R>,
        ): Map<Int, List<T>> {
            return collection.stream().collect(
                Collectors.groupingBy(
                    Function { t: T ->
                        IntStream.range(0, predicates.size)
                            .filter { i: Int -> predicates[i].test(getFun.apply(t)) }
                            .findFirst()
                            .orElse(-1)
                    })
            )
        }


        /**
         * 不同泛型差集A-B,以A的泛型返回
         *
         * @param as as
         * @param bs bs
         * @param aK a的主键
         * @param bK 汉堡王
         * @return 返回信息
         * @author zjarlin
         * @since 2022/06/29
         */
        fun <A, B> getGenericDiffs(
            `as`: Collection<A>,
            bs: Collection<B>,
            aK: Function<A, *>,
            bK: Function<B, *>?,
        ): List<A> {
            val aks = `as`.stream().map(aK).collect(Collectors.toSet())
            val bks = bs.stream().map(bK).collect(Collectors.toSet())
            val diffset = aks.stream().filter { a: Any? -> !bks.contains(a) }.collect(Collectors.toSet())
            val collect = `as`.stream().filter { a: A -> diffset.contains(aK.apply(a)) }.collect(Collectors.toList())
            return Optional.of<List<A>>(collect).filter { collection: List<A>? ->
                CollUtil.isNotEmpty(
                    collection
                )
            }.orElseGet { ArrayList() }
        }

        /**
         * 交集A&B,以A的泛型返回
         *
         * @param as as
         * @param bs bs
         * @param aK agetFun主键
         * @param bK b外键
         * @return 返回信息
         * @author zjarlin
         * @since 2022/06/29
         */
        fun <A, B> getGenericIntersection(
            `as`: Collection<A>,
            bs: Collection<B>,
            aK: Function<A, *>,
            bK: Function<B, *>?,
        ): List<A> {
            if (CollUtil.isEmpty(bs)) {
                return `as` as List<A>
            }
            val aks = `as`.stream().map(aK).collect(Collectors.toSet())
            val bks = bs.stream().map(bK).collect(Collectors.toSet())
            val diffset = aks.stream().filter { o: Any? -> bks.contains(o) }.collect(Collectors.toSet())
            val collect = `as`.stream().filter { a: A -> diffset.contains(aK.apply(a)) }.collect(Collectors.toList())
            return Optional.of<List<A>>(collect).filter { collection: List<A>? ->
                CollUtil.isNotEmpty(
                    collection
                )
            }.orElseGet { ArrayList() }
        }


        /**
         * 得到同泛型差集
         *
         * @param as 作为
         * @param bs 废话
         * @return 返回信息
         * @author zjarlin
         * @since 2022/06/29
         */
        fun <T> getGenericDiffs(`as`: Collection<T>, bs: Collection<T>): List<T> {
            return getGenericDiffs(`as`, bs, Function.identity(), Function.identity())
        }

        fun <T> getGenericIntersection(`as`: Collection<T>, bs: Collection<T>): List<T> {
            return getGenericIntersection(`as`, bs, Function.identity(), Function.identity())
        }

        /**
         * 得到B-A的差集转为A泛型合入A集合中
         *
         * @param as              主表
         * @param bs              副表
         * @param aK              主表主键
         * @param bK              副表外键(可同名字段关联)
         * @param mapstructMethod mapstruct方法
         * @return 返回信息
         * @author zjarlin
         * @since 2022/06/29
         */
        fun <A, B> aEatB(
            `as`: Collection<A>,
            bs: Collection<B>,
            aK: Function<A, *>?,
            bK: Function<B, *>,
            mapstructMethod: Function<B, A>?,
        ): List<A> {
            val genericDiffs = getGenericDiffs(bs, `as`, bK, aK)
            val collect = genericDiffs.stream().map(mapstructMethod).collect(Collectors.toList())
            return Stream.of(collect, `as`).flatMap { obj: Collection<A> -> obj.stream() }.distinct()
                .collect(Collectors.toList())
        }

        /**
         * leftjoin
         *
         * @param as              主表
         * @param bs              副表
         * @param aK              主键
         * @param bK              外键(主外键可同名)
         * @param mapstructMethod (传入mapstruct方法引用) 例如:R ab2r(A a,B b)
         * @return [List]<[R]>
         * @author zjarlin
         * @since 2022/06/29
         */
        fun <A, B, R> leftjoin(
            `as`: Collection<A>,
            bs: Collection<B>,
            aK: Function<A, *>,
            bK: Function<B, *>,
            mapstructMethod: BiFunction<A, B?, R>,
        ): List<R> {
            if (CollUtil.isEmpty(`as`)) {
                return emptyList()
            }
            val fastret =
                `as`.stream().map { a: A -> mapstructMethod.apply(a, null) }.filter { obj: R -> Objects.nonNull(obj) }
                    .distinct().collect(
                        Collectors.toList()
                    )
            if (CollUtil.isEmpty(bs)) {
                return fastret
            }
            val genericIntersection = getGenericIntersection(`as`, bs, aK, bK)
            if (CollUtil.isEmpty(genericIntersection)) {
                return fastret
            }
            //getGenericIntersection(collect, collect1);
            //final int asize = OptionalInt.of(as.size()).orElse(0);
            //final int bsize = OptionalInt.of(bs.size()).orElse(0);
            //if (asize > bsize) {return moreLeftjoinless(as, bs, aK, bK, mapstructMethod);}
            val relation: Map<*, List<B>> = bs.stream().collect(Collectors.groupingBy(bK))
            val genericDiffs = getGenericDiffs(`as`, bs, aK, bK)
            val ret =
                genericDiffs.stream().map { a1: A -> mapstructMethod.apply(a1, null) }.collect(Collectors.toList())
            val rs = Optional.of<List
            <R>>(`as`.stream().map<List<R>> { a: A ->
                val ak = aK.apply(a)
                val external = Optional.ofNullable(ak).map { key: Any? -> relation[key] }
                    .orElse(emptyList())?.stream()?.map { b: B -> mapstructMethod.apply(a, b) }
                    ?.collect(Collectors.toList())
                external?.let { ret.addAll(it) }
                Collections.unmodifiableList<R>(ret)
            }.flatMap<R> { obj: List<R> -> obj.stream() }.filter { obj: R -> Objects.nonNull(obj) }.distinct().collect(
                Collectors.toList<R>()
            )
            ).filter { collection: List<R>? ->
                CollUtil.isNotEmpty(collection)
            }.orElseGet { ArrayList() }
            if (CollUtil.isEmpty(rs)) {
                return fastret
            }
            rs.removeIf { obj: R -> Objects.isNull(obj) }
            return rs
        }

        /**
         * leftjoinless
         *
         * @param as
         * @param bs
         * @param aK
         * @param bK
         * @param mapstructMethod mapstruct方法
         * @return [List]<[R]>
         * @author zjarlin
         * @since 2022/06/20
         */
        fun <A, B, R> moreLeftjoinless(
            `as`: Collection<A>,
            bs: Collection<B>,
            aK: Function<A, *>,
            bK: Function<B, *>,
            mapstructMethod: BiFunction<A, B?, R>,
        ): List<R> {
            if (CollUtil.isEmpty(bs)) {
                return `as` as List<R>
            }
            val relation: Map<*, B> = bs.stream().collect(Collectors.toMap(bK, Function.identity()))
            return `as`.stream().map { a: A ->
                Optional.ofNullable(aK.apply(a))
                    .map { key: Any? -> relation[key] }
                    .map { b: B? -> mapstructMethod.apply(a, b) }
                    .orElseGet { mapstructMethod.apply(a, null) }
            }.filter { obj: R -> Objects.nonNull(obj) }.collect(Collectors.toList())
        }

        /**
         * 一步一判空去重的流操作
         *
         * @param coll     原集合
         * @param funChain 函数链
         * @return [Stream]<[R]>
         * @author zjarlin
         * @since 2022/06/29
         */
        @SafeVarargs
        fun <T, R> opt(coll: Collection<T>, vararg funChain: Function<in T, out R>?): Stream<R> {
            var ret = opt(coll, funChain[0])
            for (i in 1 until funChain.size) {
                ret = opt(
                    ret.collect(Collectors.toList()) as Collection<T>,
                    funChain[i]
                )
            }
            return ret
        }
    }
}
package openyabsx2


import org.grails.datastore.gorm.GormEntity
import org.hibernate.SessionFactory

/**
 * Based on https://dzone.com/articles/using-datatablesnet-grails
 * */
class DataService {
    SessionFactory sessionFactory

    /*
    headerList = [
                [name: "ID", messageBundleKey: "id", returnValuePropertyOrCode: "id", sortPropertyName: "id", hidden: true],
                [name: "PhoneNumber", messageBundleKey: "com.wowlabz.adminconsole.phoneNumber", returnValuePropertyOrCode: "ownerPhoneNumber", sortPropertyName: "ownerPhoneNumber"],
                [name: "Count", messageBundleKey: "com.wowlabz.adminconsole.count", returnValuePropertyOrCode: "count",sortPropertyName: "count"]
        ]

    * */


    def createResponseForTable(config, returnList, id, sEcho) {
        def returnMap = [:]
        try {
            returnMap.iTotalRecords = returnList.size
            returnMap.iTotalDisplayRecords = returnList.size
        } catch (exp) {
            returnMap.iTotalRecords = 10000
            returnMap.iTotalDisplayRecords = 10000
        }
        returnMap.sEcho = sEcho
        def dataReturnMap = []

        returnList.each { eachData ->
            def eachDataArr = []
            config.headerList.each { eachConfig ->
                if (eachConfig.returnValuePropertyOrCode instanceof String) {
                    eachDataArr << evaluateExpressionOnBean(eachData, "${eachConfig.returnValuePropertyOrCode}")
                } else if (eachConfig.returnValuePropertyOrCode instanceof Closure) {
                    eachDataArr << eachConfig.returnValuePropertyOrCode(eachData)
                } else {
                    eachDataArr << eachData."${eachConfig.name}"
                }
            }
            dataReturnMap << eachDataArr.collect { it as String }
        }

        returnMap.aaData = dataReturnMap
        return returnMap
    }

    def evaluateExpressionOnBean(beanValue, expression) {
        def cellValue
        if (expression.contains(".")) {
            expression.split("\\.").each {
                if (cellValue) {
                    if (cellValue?.metaClass?.hasProperty(cellValue, it))
                        cellValue = cellValue."$it"
                } else {
                    if (beanValue?.metaClass?.hasProperty(beanValue, it))
                        cellValue = beanValue."$it"
                }
            }
        } else {
            if (beanValue instanceof GormEntity) {
                try {
                    cellValue = beanValue?."$expression"
                } catch (exp) {
                    cellValue = exp.getMessage()
                }
            }
            if (beanValue?.metaClass?.hasProperty(beanValue, expression))
                cellValue = beanValue?."$expression"
        }
        return cellValue
    }

    def getPropertyNameByIndex(config, int index) {
        return config.headerList[index].sortPropertyName ?: config.headerList[index].name
    }

    List createFulltextHql(Class<? extends GormEntity> entity, String needle, Map params) {
        def ids = SearchEntry.findAllByObjectClassAndDataIlike(entity.getName(), "%$needle%").collect {
            it.id
        } as List<Long>
        if (!ids) return []
        def query = sessionFactory.currentSession.createQuery("from ${entity.getName()} where id in (${ids.join(",")}) order by ${params.sort ?: 'id'} ${params.order ?: 'desc'}")
        query.setFetchSize(params.limit ?: 0)
        query.setFirstResult(params.offset ?: 0)
        return query.getResultList()
    }

    Map<String, List<GormEntity>> searchEverywhere(String needle) {
        def result = [:]
        SearchEntry.findAllByDataIlike("%$needle%").each {
            if (!result.containsKey(it.getObjectClass())) result.put(it.getObjectClass(), [])
            result.get(it.getObjectClass()).add(it.getObjectId())
        }
        if (!result) return []
        def data = [:]
        result.each { k, v ->
            data.put(k, sessionFactory.currentSession.createQuery("from ${k} where id in (${v.join(",")})").getResultList())
        }
        return data
    }
}

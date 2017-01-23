var app = angular.module('app', []);

app.config(['$locationProvider', function($locationProvider){
    $locationProvider.html5Mode(true);
}]);

app.filter('trustAsHtml', function ($sce) {
    return function (html) {
        return $sce.trustAsHtml(html);
    };
});

app.controller('SearchCtrl', ['$scope', '$http', '$location', function ($scope, $http, $location) {
    params = $location.search();
    $scope.url = '/search'; // The url of our search

    
    if (!$scope.type) {
        $scope.type = 'Post'
    }
    var journal = params['private'];
    $scope.private = journal;
     if (journal) {
        $scope.journal = journal;
        $scope.journals = [{id:journal,journal:journal}];
    } else {
        $http.get("/journals").success(function (data, status) {
            $scope.jorunalsstatus = status;
            $scope.journals = [];
            $scope.journals .push({id:"", journal:""});

            for (var i = 0; i < data.length; i++) {
                if (data[i].last != null) {
                    data[i].formateddate = moment(data[i].last).format("MMMM Do YYYY");
                }
                data[i].id = data[i].journal;
                $scope.journals.push({id:data[i].id, journal: data[i].journal,formateddate: data[i].formateddate });
            }
            $scope.journal = params['journal'];
        })
            .error(function (data, status) {
                $scope.journals = data || "Request failed";
                $scope.jorunalsstatus = status;
            });
    }

    var currentYear = new Date().getFullYear();
    $scope.years = [];
    for(var y = 2001;y<=currentYear;y++) $scope.years.push(y)

    $scope.poster = params['poster'];

    $scope.year = parseInt(params['year']);
    $scope.keywords = params['keywords'];
    $scope.type = params['type'];

    // The function that will be executed on button click (ng-click="search()")
    $scope.search = function () {
        $scope.data = [];
        // Create the http post request
        // the data holds the keywords
        // The request is a JSON request.
        $http.get($scope.url + "?journal=" + $scope.journal + "&term=" + $scope.keywords + 
                  "&poster=" + $scope.poster + "&type=" + $scope.type + "&year=" + $scope.year + "&private=" + $scope.private).
        success(function (data, status) {
            $location.search({
                poster: $scope.poster,
                journal: $scope.journal,
                year: $scope.year,
                keywords: $scope.keywords,
                type: $scope.type,
                private: $scope.private
            });
            $scope.status = status;
            $scope.data = data;
            $scope.result = data; // Show result from server in our <pre></pre> element
            for (var i = 0; i < data.length; i++) {
                if (data[i].date != null) {
                    data[i].formateddate = moment(data[i].date).format("MMMM Do YYYY");
                }
            }
            if ($scope.result.length == 0) {
                $scope.message = "Your search did not match any documents. Search for 'жопа' instead";
            } else {
                $scope.message = "";
            }
        })
            .error(function (data, status) {
                $scope.data = data || "Request failed";
                $scope.status = status;
            });
    };
    if ($scope.poster || $scope.year || $scope.keywords || $scope.journal) {
        $scope.search()
    }
}]);
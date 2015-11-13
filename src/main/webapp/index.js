var app = angular.module('app', []);

app.filter('trustAsHtml', function ($sce) {
    return function (html) {
        return $sce.trustAsHtml(html);
    };
});

app.controller('SearchCtrl', function ($scope, $http) {
    $scope.url = '/search'; // The url of our search
    $scope.poster = '';
    $scope.journal = '';
    $scope.year = '';
    $scope.keywords = '';
    $scope.type = '';

    $http.get("/journals").
        success(function (data, status) {
            $scope.jorunalsstatus = status;
            $scope.journals = data;
            for (var i = 0; i < data.length; i++) {
                if (data[i].last != null) {
                    data[i].formateddate = moment(data[i].last).format("MMMM Do YYYY");
                }
                data[i].id = data[i].journal
            }
        })
        .
        error(function (data, status) {
            $scope.journals = data || "Request failed";
            $scope.jorunalsstatus = status;
        });

    $http.get("/news").
        success(function (data, status) {
            $scope.newsstatus = status;
            $scope.messages = data;

        })
        .
        error(function (data, status) {
            $scope.messages = data || "Request failed";
            $scope.newsstatus = status;
        });
    $http.get("/years").
        success(function (data, status) {
            $scope.yearsstatus = status;
            $scope.years = data;

        })
        .
        error(function (data, status) {
            $scope.years = data || "Request failed";
            $scope.yearsstatus = status;
        });

    // The function that will be executed on button click (ng-click="search()")
    $scope.search = function () {
        $scope.data = []
        // Create the http post request
        // the data holds the keywords
        // The request is a JSON request.
        $http.get($scope.url + "?journal=" + $scope.journal + "&term=" + $scope.keywords + "&poster=" + $scope.poster + "&type=" + $scope.type+"&year="+$scope.year).
            success(function (data, status) {
                $scope.status = status;
                $scope.data = data;
                $scope.result = data; // Show result from server in our <pre></pre> element
                for (var i = 0; i < data.length; i++) {
                    if (data[i].date != null) {
                        data[i].formateddate = moment(data[i].date).format("MMMM Do YYYY");
                    }
                }
            })
            .
            error(function (data, status) {
                $scope.data = data || "Request failed";
                $scope.status = status;
            });
    };
});